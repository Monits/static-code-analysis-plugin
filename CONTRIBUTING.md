# Contributing to SCA

First off, thanks for taking the time to contribute!

The following is a set of guidelines for contributing to SCA. These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

## Manual testing

### Publish a local version
1. Check plugin version at [build.gradle@version](/build.gradle). i.e. `2.7.1-SNAPSHOT
2. Build the plugin
```
./gradlew assemble
```

### Add it to a real project
1. Add SCA's build dir to your project's buildscript at your root `build.gradle`.

```groovy
buildscript {
    repositories {
        // ...
        flatDir dirs: '/Users/ltorvalds/Projects/static-code-analysis-plugin/build/libs'``
        // ...
    }
```

2. That's it! Sync your project and try your changes.