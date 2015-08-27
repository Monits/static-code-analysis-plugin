# Static Code Analysis

Static Code Analysis is a Gradle plugin that encapsulates CheckStyle,
Findbugs, PMD and CPD plugins, focusing on efficiency and easy configuration
by exposing a simple DSL.

# Add to Project

Add the dependency by adding our maven repositories

```
repositories {
    maven {
        url 'http://nexus.monits.com/content/repositories/oss-releases'
    }
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

##History

#v1.4.1

* FB_CONTRIB updated to v6.2.3

#v1.4.0

* PMD updated to v5.1.3
* PMD for Gradle < 2.4

#v1.3.2

* Checkstyle uses v6.9 if Gradle v2.7 is used. Else, it runs with v6.7.

#v1.3.1

* FB_CONTRIB updated to v6.2.2

#v1.3

* Plugin can be configured with ``ignoreErrors`` parameter to stop build if errors are found.

#v1.2

* PMD & CPD compatible with Gradle v2.3+. It resolves dependencies and tool versions according to
the used gradle version.

## Why use it?

Static Code Analysis offers new features and extensions to the encapsulated plugins. For instance,
it extends Findbugs scope by combining it with [FB-CONTRIB] (https://github.com/mebigfatguy/fb-contrib)
and our very own [Monits Findbugs] (https://github.com/Monits/findbugs-plugin), both which add an
insane amount of detectors. Making it dependant of the *mockableAndroidJarTask*, it has enabled Findbugs
to do a more complex analysis because it has access to Android's SDK classes. Efficiency wise, it includes
auto generated files (every ``R.class``, ``Manifest.class`` and ``BuildConfig.class``) as part of the
classpath but not in its analysis, so Findbugs doesn't run every time a resource is modified.

Moreover, Checkstyle now supports remote file configuration.

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

    findbugsExclude = new File("$project.rootProject.projectDir/config/findbugs/excludeFilter.xml")
    checkstyleRules = "http://static.monits.com/checkstyle.xml"
    pmdRules = [ "http://static.monits.com/pmd.xml", "http://static.monits.com/pmd-android.xml" ]
}
</code>

There are things to consider though, like running plugins are always set to ``true`` by default.
All configurations values in the example are the default ones, but you must take notice of their types;
``findbugsExclude`` is a ``File``, ``checkstyleRules`` is a ``String`` (Note: for remote files, it must
begin with "http://" or "https://", else it will be considered local) and ``pmdRules`` is a
collection of ``String``.

As of version 1.3, ``ignoreErrors`` decides whether the build is stopped if errors are reported. Its default
value is ``true`` meaning that it will continue the build regardless of reported errors.

And that's it! As always feel free to contribute in any shape or form, we look forward to your feedback!.



