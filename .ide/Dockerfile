FROM maven:3.9-eclipse-temurin-17

# 安装 code-server 和 vscode 常用插件
RUN curl -fsSL https://code-server.dev/install.sh | sh \
  && code-server --install-extension cnbcool.cnb-welcome \
  && code-server --install-extension redhat.vscode-yaml \
  && code-server --install-extension dbaeumer.vscode-eslint \
  && code-server --install-extension waderyan.gitblame \
  && code-server --install-extension mhutchie.git-graph \
  && code-server --install-extension donjayamanne.githistory \
  && code-server --install-extension cloudstudio.live-server \
  && code-server --install-extension tencent-cloud.coding-copilot \
  && code-server --install-extension vscjava.vscode-java-pack \
  && echo done \
  # 安装 ssh 服务 ，用于支持 VSCode 等客户端通过 Remote-SSH 访问开发环境（也可按需安装其他软件）
  && apt-get update \
  && apt-get --no-install-recommends install -y git-lfs dnsutils htop lsof net-tools nload openssh-server unzip wget zsh

# 指定字符集支持命令行输入中文（根据需要选择字符集）
ENV LANG C.UTF-8
ENV LANGUAGE C.UTF-8
