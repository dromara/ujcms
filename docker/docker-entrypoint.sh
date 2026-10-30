#!/usr/bin/env bash
set -Eeuo pipefail

uid="$(id -u)"
gid="$(id -g)"
echo >&2 "Current user: $uid:$gid"
echo >&2 "Current dir: $PWD"

# 复制目录树（文件归当前用户，镜像里的属主不带入挂载目录）
copy_tree() { # <源目录> <目标目录>（目标须已存在）
  local sourceTarArgs=(
    --create
    --file -
    --directory "$1"
    --owner "$uid" --group "$gid"
  )
  local targetTarArgs=(
    --extract
    --file -
    --directory "$2"
  )
  if [ "$uid" != '0' ]; then
    # avoid "tar: .: Cannot utime: Operation not permitted" and "tar: .: Cannot change mode to rwxr-xr-x: Operation not permitted"
    targetTarArgs+=( --keep-old-files )
  fi
  tar "${sourceTarArgs[@]}" . | tar "${targetTarArgs[@]}"
}

# 解包主题包：优先 unzip（Dockerfile 安装），没有则用 python3（libreoffice 的依赖）；两者都没有时主题包同步跳过
zip_tool=''
if command -v unzip >/dev/null 2>&1; then
  zip_tool=unzip
elif command -v python3 >/dev/null 2>&1; then
  zip_tool=python3
fi
zip_cat() { # <zip> <包内路径>：成员内容写到标准输出
  if [ "$zip_tool" = unzip ]; then
    unzip -p "$1" "$2"
  else
    python3 -c 'import sys, zipfile; sys.stdout.buffer.write(zipfile.ZipFile(sys.argv[1]).read(sys.argv[2]))' "$1" "$2"
  fi
}
zip_extract() { # <zip> <目标目录>
  if [ "$zip_tool" = unzip ]; then
    unzip -q -o "$1" -d "$2"
  else
    python3 -m zipfile -e "$1" "$2"
  fi
}

# 整目录替换：先填充同级的 <目标>.staging-sync（主题注册表扫描时跳过 .staging- 目录），再换入，不留半成品
replace_dir() { # <源：目录或 .zip> <目标目录>
  local staging="$2.staging-sync"
  rm -rf "$staging"
  mkdir -p "$staging"
  if [ -d "$1" ]; then
    copy_tree "$1" "$staging"
  else
    zip_extract "$1" "$staging"
  fi
  rm -rf "$2"
  mv "$staging" "$2"
}

# 主题同步（UJCMS_THEME_SYNC=true，可选）：换镜像后的首次启动，把镜像内置的主题目录与主题包覆盖到已安装的同名主题实例上，
# 模板随发版更新。演示 / 测试站用；正式站勿开——控制台里改过的模板文件会被镜像版本覆盖。不动数据库、uploads/ 与主题包库存量包
sync_themes() {
  shopt -s nullglob
  local siteDir site themeDir theme libraryDir zips zip name inst count=0
  # 1) 镜像 webapp 自带的主题：themes/<站点ID>/<主题名>（站点 1 的 default）整目录替换；themes/error 一类非站点目录同样整目录替换
  for siteDir in /usr/src/ujcms/themes/*/; do
    siteDir="${siteDir%/}"
    site="${siteDir##*/}"
    if [[ "$site" =~ ^[0-9]+$ ]]; then
      for themeDir in "$siteDir"/*/; do
        themeDir="${themeDir%/}"
        theme="${themeDir##*/}"
        echo >&2 "Theme sync: themes/$site/$theme <- image"
        mkdir -p "static/themes/$site"
        replace_dir "$themeDir" "static/themes/$site/$theme"
        count=$((count + 1))
      done
    else
      echo >&2 "Theme sync: themes/$site <- image"
      mkdir -p static/themes
      replace_dir "$siteDir" "static/themes/$site"
      count=$((count + 1))
    fi
  done
  # 2) 主题包目录（镜像内置或挂载的 *.zip）：按包内 theme.manifest.json 的 themeName 找到各站点已安装的同名实例
  #    （static/themes/<站点ID>/<themeName>）整目录替换；没装过该主题的站点不新建
  libraryDir="${UJCMS_INSTALL_LIBRARYDIR:-/ujcms/theme-packages}"
  zips=()
  if [ -d "$libraryDir" ]; then
    zips=("$libraryDir"/*.zip)
  fi
  if [ "${#zips[@]}" -gt 0 ] && [ -z "$zip_tool" ]; then
    echo >&2 "WARNING: theme sync skipped $libraryDir/*.zip - neither unzip nor python3 is available"
    zips=()
  fi
  for zip in "${zips[@]}"; do
    name="$(zip_cat "$zip" theme.manifest.json 2>/dev/null | sed -n 's/.*"themeName"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1 || true)"
    if [ -z "$name" ]; then
      echo >&2 "WARNING: theme sync skipped $zip - no themeName in theme.manifest.json"
      continue
    fi
    for inst in static/themes/*/"$name"/; do
      inst="${inst%/}"
      site="${inst%/*}"
      site="${site##*/}"
      [[ "$site" =~ ^[0-9]+$ ]] || continue
      echo >&2 "Theme sync: themes/$site/$name <- $zip"
      replace_dir "$zip" "$inst"
      count=$((count + 1))
    done
  done
  shopt -u nullglob
  echo >&2 "Theme sync complete: $count theme directories replaced"
}

# 首次启动（挂载目录为空）：复制初始文件
if [ ! -d static/uploads ] && [ ! -d static/themes ] && [ ! -d static/cp ] \
    && [ ! -e static/index.html ] && [ ! -e static/index.htm ]; then

  echo >&2 "UJCMS init files not found in $PWD/static - copying now..."
  if [ -n "$(find static -mindepth 1 -maxdepth 1 -not -name 'lost+found')" ]; then
    echo >&2 "WARNING: $PWD/static is not empty! (copying anyhow)"
  fi
  copy_tree /usr/src/ujcms /ujcms/static
  echo >&2 "Complete! UJCMS init files has been successfully copied to $PWD/static"
fi

theme_sync=false
case "${UJCMS_THEME_SYNC:-}" in
  [Tt][Rr][Uu][Ee]|1|[Yy][Ee][Ss]|[Oo][Nn]) theme_sync=true ;;
esac

# 镜像是否换过：构建时写入 cp/.timestamp，与挂载目录里的不同（或挂载目录里没有）即为新镜像的首次启动
if [ -e /usr/src/ujcms/cp/.timestamp ] && ! cmp -s static/cp/.timestamp /usr/src/ujcms/cp/.timestamp 2>/dev/null; then
  # 主题同步放在 cp 迁移之前：中途失败时 .timestamp 仍是旧值，下次启动会重试
  if [ "$theme_sync" = true ]; then
    sync_themes
  fi
  echo >&2 "Migrating UJCMS cp from old version..."
  mkdir -p static/cp
  find static/cp -mindepth 1 -delete
  copy_tree /usr/src/ujcms/cp /ujcms/static/cp
  echo >&2 "Migration UJCMS cp complete!"
elif [ "$theme_sync" = true ]; then
  echo >&2 "Theme sync: image unchanged (cp/.timestamp matches), nothing to do - delete static/cp/.timestamp and restart to force"
fi

echo >&2 "Starting UJCMS..."
exec "$@"
