# Static Code Analysis

[![Download](https://api.bintray.com/packages/monits/monits-android/static-code-analysis-plugin/images/download.svg) ](https://bintray.com/monits/monits-android/static-code-analysis-plugin/_latestVersion)

Static Code Analysis is a Gradle plugin that encapsulates CheckStyle,
Findbugs, PMD and CPD plugins, focusing on efficiency and easy configuration
by exposing a simple DSL.

# Add to Project

Add the jcenter repository

```
repositories {
    jcenter()
}
```
Then add the plugin as dependency

```
dependencies {
    classpath 'com.monits:static-code-analysis-plugin:1.+'
}
```

And apply it

```
apply plugin: 'com.monits.staticCodeAnalysis'
```

For older versions you need to use our repository.
```
repositories {
    maven {
        url 'http://nexus.monits.com/content/repositories/oss-releases'
    }
}
```

## History

# v1.5

* **1.5.14**
  * Update checkstyle to 6.14

* **1.5.13**
  * Update fb-contrib to 6.4.1

* **1.5.12**
  * Update PMD to 5.4.1

* **1.5.11**
  * Update Checkstyle to 6.13

* **1.5.10**
  * Update Checkstyle to 6.12.1
  * Update fb-contrib to 6.4.0

* **1.5.9**
  * Update Checkstyle to 6.12

* **1.5.8**
  * Publish sources and Javadocs

* **1.5.7**
  * Move to Bintray

* **1.5.6**
  * Add license gradle plugin 0.12.1

* **1.5.5**
  * Add classpath to PMD for Gradle >= 2.8

* **1.5.4**
  * PMD updated to v5.4.0

* **1.5.3**
  * Checkstyle for Gradle >= 2.7 updated to 6.11.1

* **1.5.2**
  * PMD for Gradle >= 2.4 updated to 5.3.4

* **1.5.1**
  * Bug fix: Checkstyle rules compatible with versions grater than 6.7

* **1.5.0**
  * Findbugs accepts remote location for ``findbugsExclude``

# v1.4

* **1.4.4**
  * Bug fix: doesn't crash when ``findbugsExclude`` is not defined

* **1.4.3**
  * Bug fix: now it works with JDK 1.7

* **v1.4.2**
  * Checkstyle updated to v6.10.1

* **1.4.1**
  * FB_CONTRIB updated to v6.2.3

* **1.4.0**
  * PMD updated to v5.1.3
  * PMD for Gradle < 2.4

# v1.3

* **1.3.2**
  * Checkstyle uses v6.9 if Gradle v2.7 is used. Else, it runs with v6.7.

* **1.3.1**
  * FB_CONTRIB updated to v6.2.2

* **1.3.0**
  * Plugin can be configured with ``ignoreErrors`` parameter to stop build if errors are found.

# v1.2

* **1.2.0**
  * PMD & CPD compatible with Gradle v2.3+. It resolves dependencies and tool versions according
   to the used gradle version.

## Why use it?

Static Code Analysis offers new features and extensions to the encapsulated plugins. For instance,
it extends Findbugs scope by combining it with [FB-CONTRIB] (https://github.com/mebigfatguy/fb-contrib)
and our very own [Monits Findbugs] (https://github.com/Monits/findbugs-plugin), both which add an
insane amount of detectors. Making it dependant of the *mockableAndroidJarTask*, it has enabled Findbugs
to do a more complex analysis because it has access to Android's SDK classes. Efficiency wise, it includes
auto generated files (every ``R.class``, ``Manifest.class`` and ``BuildConfig.class``) as part of the
classpath but not in its analysis, so Findbugs doesn't run every time a resource is modified.

Moreover, Checkstyle and Findbugs now support remote file configuration.

##DSL
Configuring Static Code Analysis is very simple and intuitive thanks to its DSL. You can choose
which encapsulated plugin to run and set its configuration files. Here is a quick example

<code>
staticCodeAnalysis {
    findbugs = true
    checkstyle = true
    pmd = true
    cpd = false

    ignoreErrors = true

    findbugsExclude = "$project.rootProject.projectDir/config/findbugs/excludeFilter.xml"
    checkstyleRules = "http://static.monits.com/checkstyle.xml"
    pmdRules = [ "http://static.monits.com/pmd.xml", "http://static.monits.com/pmd-android.xml" ]
}
</code>

There are things to consider though, like running plugins are always set to ``true`` by default.
All configurations values in the example are the default ones, but you must take notice of their types;
``findbugsExclude`` and ``checkstyleRules`` are a ``String`` (Note: for remote files, they must
begin with "http://" or "https://", else it will be considered local) and ``pmdRules`` is a
collection of ``String``.

As of version 1.3, ``ignoreErrors`` decides whether the build is stopped if errors are reported. Its default
value is ``true`` meaning that it will continue the build regardless of reported errors.

And that's it! As always feel free to contribute in any shape or form, we look forward to your feedback!.

# Copyright and License
Copyright 2010-2015 Monits.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this work except in compliance with the License. You may obtain a copy of the
License at:

http://www.apache.org/licenses/LICENSE-2.0

