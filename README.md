### This is an unofficial LSP for Slang to use with jetbrain IDEs.

This repository is the **Thurinum fork** of [16-Bit-Dog/slang-jetbrains-extension](https://github.com/16-Bit-Dog/slang-jetbrains-extension), used under the Apache License 2.0. See [LICENSE](./LICENSE) and [NOTICE](./NOTICE).

---

### Example
![Example](./images/example.gif)

---

### Jetbrains plugin distribution page
- This fork: not published yet (build it locally with `./gradlew buildPlugin`)
- Original plugin by 16-Bit-Dog: https://plugins.jetbrains.com/plugin/26239-slang-unofficial-

---

### To Build
1. install Intellij IDEA 2024.x.x (or use a standalone Gradle 8.10+)
2. fetch code with `git clone https://github.com/Thurinum/slang-jetbrains-extension.git`
3. build with `./gradlew buildPlugin` (the ZIP is written to `build/distributions`)
4. as a note, the "build & run" task may fail (jetbrains bug?). Re-run the task if this happens.

---

### If highlighting does not work, try the following:
(1) delete the Slang extension
(2) reinstall the Slang extension (latest version)

---

### To Run Tests
The project contains native Java tests to verify Lexer and Syntax Highlighter correctness. To run the tests, use the included Gradle wrapper (requires JDK 21):

**On Windows:**
```cmd
./gradlew test
```

**On macOS/Linux:**
```bash
./gradlew test
```

