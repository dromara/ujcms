#!/usr/bin/env bash
set -Eeuo pipefail

uid="$(id -u)"
gid="$(id -g)"
echo >&2 "Current user: $uid:$gid"
echo >&2 "Current dir: $PWD"

# 检查 cp 是否需要更新
if [[ -e static/cp/.timestamp && -e /usr/src/ujcms/cp/.timestamp ]]; then
  if ! cmp -s static/cp/.timestamp /usr/src/ujcms/cp/.timestamp; then
    echo >&2 "Migrating UJCMS cp from old version..."
    rm -rf static/cp/*

    # cp /usr/src/ujcms/cp/* /ujcms/static/cp/
    sourceTarArgs=(
      --create
      --file -
      --directory /usr/src/ujcms/cp
      --owner "$uid" --group "$gid"
    )
    targetTarArgs=(
      --extract
      --file -
      --directory /ujcms/static/cp
    )
    tar "${sourceTarArgs[@]}" . | tar "${targetTarArgs[@]}"
    echo >&2 "Migration UJCMS cp complete!"
  fi
fi

# 检查初始文件是否存在
if [ ! -d static/uploads ] && [ ! -d static/templates ] && [ ! -d static/cp ] \
    && [ ! -e static/index.html ] && [ ! -e static/index.htm ]; then

  echo >&2 "UJCMS init files not found in $PWD/static - copying now..."
  if [ -n "$(find static -mindepth 1 -maxdepth 1 -not -name 'lost+found')" ]; then
    echo >&2 "WARNING: $PWD/static is not empty! (copying anyhow)"
  fi

  # cp /usr/src/ujcms/* /ujcms/static/
  sourceTarArgs=(
    --create
    --file -
    --directory /usr/src/ujcms
    --owner "$uid" --group "$gid"
  )
  targetTarArgs=(
    --extract
    --file -
    --directory /ujcms/static
  )
  if [ "$uid" != '0' ]; then
    # avoid "tar: .: Cannot utime: Operation not permitted" and "tar: .: Cannot change mode to rwxr-xr-x: Operation not permitted"
    targetTarArgs+=( --keep-old-files )
  fi
  tar "${sourceTarArgs[@]}" . | tar "${targetTarArgs[@]}"
  echo >&2 "Complete! UJCMS init files has been successfully copied to $PWD/static"
fi

echo >&2 "Starting UJCMS..."
exec "$@"
