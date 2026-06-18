# 开发环境依赖(writing-with-ai)

> 新机器 / 新 clone 仓库之后,**开工 M0 之前**必须装的依赖。任何开发机 / CI / 朋友机器都一样。

## 1. 系统要求

- **macOS**(本仓库 v1 仅 macOS 开发验证,理论上 Linux / WSL2 也可;Windows 未验证)。
- **Homebrew**(macOS 包管理)。`/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"` 安装。
- **JDK 17** — Android Gradle Plugin 8.5+ 强制要求。
- **Gradle 9.x**(只用来生成 wrapper;之后用 `./gradlew` 即可)。

## 2. 安装

### 2.1 JDK 17(openjdk@17)

```bash
brew install openjdk@17
```

> **不要**用 `brew install --cask temurin@17` —— cask 需要 sudo 装到 `/Library/Java/JavaVirtualMachines/`,在无密码 / 远程环境下会失败。
> `openjdk@17` 装到 `/opt/homebrew/opt/openjdk@17/`,无需 sudo。

### 2.2 配置 JAVA_HOME

`openjdk@17` 是 keg-only(不自动 link),需要把 `JAVA_HOME` 加到 shell:

```bash
cat >> ~/.zshrc <<'EOF'

# writing-with-ai: openjdk@17 (Java 17 for Android Gradle)
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$PATH"
EOF
source ~/.zshrc
```

如果用 bash,把 `~/.zshrc` 换成 `~/.bash_profile`。

### 2.3 Gradle(只用于生成本仓库的 wrapper)

```bash
brew install gradle
```

> Gradle CLI 一次性使用:运行 `gradle wrapper --gradle-version <version>` 生成项目内的 `gradlew` / `gradlew.bat` / `gradle/wrapper/` 后,所有后续命令都走 `./gradlew`,**不再**用本机 `gradle`。
>
> 仓库提交 wrapper 后,**wrapper 里 pin 的 Gradle 版本**才是构建用的真实版本,与本机 `gradle` 版本无关。本机 `gradle` 只用来"生成 wrapper"那一次。

## 3. 验证

```bash
java -version      # 应输出 openjdk 17.x.x
javac -version     # 应输出 javac 17.x.x
gradle --version   # 应输出 Gradle 9.x
```

三行都通过 → 进入 [CLAUDE.md §"命令"](../../CLAUDE.md) 跑 `./gradlew :app:assembleDebug` 验证工程本身。

## 4. 常见问题

- **java -version 报 "Unable to locate a Java Runtime"**:
  - 原因:`/usr/bin/java` 是 macOS 系统 stub,需要 `$JAVA_HOME/bin/java` 在 PATH 上。
  - 解决:确认 `~/.zshrc` 已加 `export PATH="$JAVA_HOME/bin:$PATH"` 且 source 过。
- **`brew install --cask temurin@17` 卡在 sudo**:
  - 不要用 cask,改用 `brew install openjdk@17`(formula,无需 sudo)。
- **Gradle 与 AGP 不兼容**:
  - 升级 AGP ≥ 8.7 + 用 Gradle wrapper 8.9+;AGP 与 Gradle 版本对齐见 [Android 官方对照表](https://developer.android.com/build/releases/gradle-plugin)。
- **ANDROID_HOME / SDK 未配**:
  - M0 不需要(只编 Debug APK 用 AGP 自带 build-tools);M2 起需要装 Android SDK,届时在 CLAUDE.md §"命令" 旁加 §"SDK 安装" 段。

## 5. 项目特定说明

- **本仓库不依赖 Android Studio 命令行**(只是开发推荐)。`./gradlew` 命令对所有 CI / 命令行场景可用。
- **本仓库不依赖本地 Android SDK 即可跑**(`assembleDebug` 会自动下载 build-tools / platform-tools)。仅当用户要 `adb install` 到真机时才需要 `ANDROID_HOME`。
- **JDK 必须 17**,不能用 21 / 25;AGP 8.5+ 在 21 上仍能编,但 `gradle daemon` 偶发崩溃在 Kotlin 2.x KSP 处理时。