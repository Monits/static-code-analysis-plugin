## History

# v2.6
### 2.6.11 - UNRELEASED

Improvements:
 * Upgraded PMD to 6.15.0

### 2.6.10

Improvements:
 * Upgraded to Gradle 5.1.1
 * Upgraded Checkstyle to 8.19
 * Upgraded PMD to 6.13.0

### 2.6.9

Improvements:
 * Upgraded to Gradle 4.10.2
 * Upgraded PMD to 6.8.0
 * Upgraded Checkstyle to 8.13
 * All lint tasks (ie: lintDebug and lintRelease) are now cacheable and can be up-to-date

### 2.6.8

Improvements:
 * Upgraded PMD to 6.7.0
 * Upgraded Checkstyle to 8.12
 * Upgraded to Gradle 4.10
 * The plugin configuration time has been greatly improved. Improvements of up to 4X have been measured

### 2.6.7

Bug fixes:
 * Consider `testCompileOnly` and `androidTestCompileOnly` too

### 2.6.6

Improvements:
 * Upgraded to Gradle 4.9
 * Fully support `api` and `implementation` configurations
 * Upgraded fb-contrib to 7.4.3
 * Upgraded Checkstyle to 8.11
 * Android projects that depend on a Java module in mixed multimodule projects
 * `compileOnly` is now used instead of `provided`

Bug fixes:
 * PMD now properly receives compiled class info for Android projects

### 2.6.5

Improvements:
 * Upgraded to Gradle 4.8
 * Upgraded PMD to 6.5.0
 * Upgraded Checkstyle to 8.10.1

### 2.6.4

Improvements:
 * Upgraded Checkstyle to 8.10
 * Upgraded fb-contrib to 7.4.1
 * Upgraded PMD to 6.4.0

Bug fixes:
 * [#26](https://github.com/Monits/static-code-analysis-plugin/issues/26) - Standard java projects download android configuration

### 2.6.3

 Improvements:
 * Upgraded Checkstyle to 8.9

Bug fixes:
 * [#23](https://github.com/Monits/static-code-analysis-plugin/issues/23) - IllegalStateException: Resolving configuration 'releasePublish' directly is not allowed under AGP 3.0.0

### 2.6.2

 No changes, just upgraded Gradle Portal plugin version to fix some issues with plugin uploading

### 2.6.1

 Bug fixes:
 * Android no longer applies the Java suppression for Findbugs by default

### 2.6.0

 Improvements:
 * Upgraded to fully support Gradle 4.+
 * Upgraded PMD to 5.8.1
 * Upgraded Checkstyle to 8.2
 * Upgraded fb-contrib to 7.0.5
 * Java projects now use a default suppression similar to the Android one, favoring
    PMD checks when duplicate, and suppressing rules that are broken. Tests also
    have a more lax configurartion out of the box.
 * Changed default Android Lint warning `WifiManagerPotentialLeak` to error.
 * Fully supports Android Gradle plugin 3.0.0-beta4

 Bug fixes:
 * [#16](https://github.com/Monits/static-code-analysis-plugin/issues/16) - Remove usage of all deprecated Gradle APIs
 * A regression under Gradle 4.x where checkstyle reports where incorrectly considered out of date.

# v2.5
### 2.5.0

 Improvements:
 * The plugin can properly handle Android's build-cache
 * Upgraded Checkstyle to 7.8.2
 * Upgraded fb-contrib to 7.0.2

 Bug fixes:
 * Fix [#17](https://github.com/Monits/static-code-analysis-plugin/issues/17) - The plugin now works with Android's build-cache

# v2.4
### 2.4.2

 Improvements:
 * Upgraded PMD to 5.7.0
 * Upgraded Checkstyle to 7.7
 * Upgraded fb-contrib to 7.0.1

### 2.4.1

 Improvements:
 * Upgraded PMD to 5.5.4
 * Upgraded Checkstyle to 7.6
 * Upgraded fb-contrib to 6.8.4

 Bug fixes:
 * Fix [#13](https://github.com/Monits/static-code-analysis-plugin/issues/13) - Running `--offline` does not even try to hit the network anymore

### 2.4.0

 Improvements:
 * All default configs are now mantained and downloaded from GitHub itself!
 * Upgraded fb-contrib to 6.8.3

# v2.3

### 2.3.5

 Improvements:
 * Upgraded PMD to 5.5.3
 * Upgraded Checkstyle to 7.5.1

### 2.3.4

 Improvements:
 * Checkstyle tasks are configured with a 'checkstyle.cache.file' extension property per sourceset to configure
    cache file in the config if desired.
 * Default Checkstyle config for latest Checkstyle makes use of the local analysis cache property.
 * Upgraded fb-contrib to 6.8.2
 * Upgraded Checkstyle to 7.4

 Bug fixes:
 * Backported Checkstyle classpath fix from Gradle 3.3. [See original issue](https://github.com/gradle/gradle/issues/855)
 * Fix [#10](https://github.com/Monits/static-code-analysis-plugin/issues/10) - The plugin can now be applied before the Java / Android
plugins are applied.
 * Fix NullPointerException after a module dependency has been removed or deleted.

### 2.3.3

 Bug fixes:
 * Reverted to PMD 5.5.1, since 5.5.2 introduced a NPE in CPD. [See related issue](https://sourceforge.net/p/pmd/bugs/1542/)

### 2.3.2

 Improvements:
 * Upgraded PMD to 5.5.2
 * Stop using Gradle "<<" tasks operator, that's deprecated and will be removed in Gradle 4

### 2.3.1

 Bug fixes:
 * Android plugin 2.2.0 no longer warns on the usage of `useJack`

 Improvements:
 * Android Lint task is now treated as a @CacheableTask under Gradle 3+
 * Upgraded Checkstyle to 7.2
 * Upgraded Monits' Findbugs plugin to 0.2.0
 * Upgraded fb-contrib to 6.8.0
 * No longer need Monits' SNAPSHOTS maven repository, the Monits' Findbugs plugin is in both jcenter and maven central

### 2.3.0

 Bug fixes:
 * Fixed a performance regression when using Findbugs under Gradle 3.X

 Improvements:
 * Upgraded Checkstyle to 7.1.1
 * Upgraded fb-contrib to 6.6.3
 * CPD task makes better use of Gradle caching mechanisms, providing greatly increased performance

# v2.2
### 2.2.1

 Bug fixes:
 * Don't change `provided` configuration if it already exists

### 2.2.0

 Bug fixes:
 * Android lint outputs no longer includes "fatal" reports, which are generated by `:lintVital*` tasks
 * DownloadTask refreshes the file contents when config changes regardless of last modification date of each file

 Improvements:
 * CPDTask can now ignore literals, identifiers and configure language.
    Plugin defaults to java, ignoring literals and identifiers.
 * Android lint rules XML can now be confgured through DSL. Supports remote files.
 * Android lint honors `ignoreErrors` settings.
 * Android lint can be disabled / enabled through the DSL. When disabled, the task will simply be SKIPPED.
    `LintVital*` tasks will still run, since that is part of the build step for release builds, not the check step.
 * Android will not warn for usage of deprecated `useJack` option anymore.
 * Upgraded Checkstyle to 7.1
 * Upgraded fb-contrib to 6.6.2
 * PMD can now cache remote config files, and use them when offline.
 * Android lint XML report is now written under `$buildDir/reports/android/android-lint.xml`
    for consistency with other tools. This name is the same regardless of the used plugin version.
    HTML report is untouched.

# v2.1
### 2.1.8

 Bug fixes:
 * Android lint outputs are now properly set regardless of execution order

 Improvements:
 * Upgraded PMD to 5.5.1

### 2.1.7

 Bug fixes:
 * Allow for dependencies and configurations being added late in the configuration process.
 * Checkstyle 6.19 should still use the latest checkstyle ruleset by default.

### 2.1.6

 Bug fixes:
 * Add compatibility with Android Gradle 2.2.+. See [#6](https://github.com/Monits/static-code-analysis-plugin/issues/6)
 * Fix idea project import, dependency scopes are honored
 * Fix some typos in messages

### 2.1.5

 Improvements:
 * PMD updated to 5.5.0
 * Checkstyle updated to 7.0 when using JRE 8+. If using JRE7, 6.19 is used, and an update warning issued.

### 2.1.4

 Bug fixes:
 * Improve classpath configuration for PMD on Android tests

### 2.1.3

 Bug fixes:
 * Improve classpath configuration for PMD on Android tests
 * CPD can now be run without having PMD running

### 2.1.2

 Bug fixes:
 * Fix compatibility with Gradle 2.14

### 2.1.1

 Improvements:
 * Upgraded PMD to 5.4.2
 * Upgraded Checkstyle to 6.19
 * Include compiled .class files to PMD analysis (backported from 3.+) to fix false positives.
    See https://github.com/gradle/gradle/pull/649 and https://sourceforge.net/p/pmd/bugs/1468/

### 2.1.0

 Improvements:
 * We manually inject inputs and outputs to Android Lint tasks to allow it to report up-to-date when there are no changes.

# v2.0
### 2.0.1

 Improvements:
 * Finbugs classpath and analyzed classes has been restricted. We get the same results, but with faster analysis.

 Bug fixes:
 * Findbugs reports only show issues in clases belonging to the corresponding sourceset.
 * Extra Android lint rules are properly configured on old ([1.0, 1.3)) versions of the Android Gradle plugin

### 2.0.0

 New features:
 * Per-sourceset rules can now be configured by extended DSL. Reports are also generated by sourceset
 * Non-multimoule projects are now supported
 * The plugin is now fully compatible with plain Java projects
 * Remote checkstyle / findbugs config is downloaded gziped on Gradle 2.13 if possible
 * If there is no connectivity, downloading checkstyle / findbugs config will not fail the build if there is a previous downloaded version. A warning will be issued
 * PMD rules can now be configured as relative file paths
 * Default PMD rules are now configured according to the PMD version being used
 * Remote checkstyle / findbugs config download now honors the `--offline` flag
 * Files are now downloaded only once per run, regardless of number of projects / sourcesets configured
 * Warnings are issued when the version of gradle used is too old to support the latest tools
 * All tasks defined under the plugin are now parallelizable

 Bug fixes:
 * Fix classpath population on an clean project
 * Fix classpath population on Android proejcts to include generated classes in classpath (though still not analyzed)
 * Fix path to mockable-androir-xx.jar under Android Gradle plugin 2.+
 * Fix NPE when trying to not use a findbugs filter


# v1.6
### 1.6.5
 * Update checkstyle to 6.18

### 1.6.4
 * Remote config is now downloaded with a conditional request 
 * Update checkstyle to 6.17
 * Update fb-contrib to 6.6.1

### 1.6.3
 * Works with Gradle 2.10 or later as intended 

### 1.6.2
 * Botched release. Don't use.

### 1.6.1
 * Update Checkstyle to 6.15
 * Update fb-contrib to 6.6.0

### 1.6.0
 * PMD / Findbugs ignore changes to resources in module dependencies, just as it does for it's own.
 * Custom jars with Android lint rules can be specified as dependencies under `androidLint`

# v1.5

### 1.5.16
  * Make sure Monits' repository is added to resolve dependencies
  * Upgrade to Checkstyle 6.14.1

### 1.5.15
  * CPD no longer fails when no java files are found

### 1.5.14
  * Update checkstyle to 6.14

### 1.5.13
  * Update fb-contrib to 6.4.1

### 1.5.12
  * Update PMD to 5.4.1

### 1.5.11
  * Update Checkstyle to 6.13

### 1.5.10
  * Update Checkstyle to 6.12.1
  * Update fb-contrib to 6.4.0

### 1.5.9
  * Update Checkstyle to 6.12

### 1.5.8
  * Publish sources and Javadocs

### 1.5.7
  * Move to Bintray

### 1.5.6
  * Add license gradle plugin 0.12.1

### 1.5.5
  * Add classpath to PMD for Gradle >= 2.8

### 1.5.4
  * PMD updated to v5.4.0

### 1.5.3
  * Checkstyle for Gradle >= 2.7 updated to 6.11.1

### 1.5.2
  * PMD for Gradle >= 2.4 updated to 5.3.4

### 1.5.1
  * Bug fix: Checkstyle rules compatible with versions grater than 6.7

### 1.5.0
  * Findbugs accepts remote location for ``findbugsExclude``

# v1.4

### 1.4.4
  * Bug fix: doesn't crash when ``findbugsExclude`` is not defined

### 1.4.3
  * Bug fix: now it works with JDK 1.7

### v1.4.2
  * Checkstyle updated to v6.10.1

### 1.4.1
  * FB_CONTRIB updated to v6.2.3

### 1.4.0
  * PMD updated to v5.1.3
  * PMD for Gradle < 2.4

# v1.3

### 1.3.2
  * Checkstyle uses v6.9 if Gradle v2.7 is used. Else, it runs with v6.7.

### 1.3.1
  * FB_CONTRIB updated to v6.2.2

### 1.3.0
  * Plugin can be configured with ``ignoreErrors`` parameter to stop build if errors are found.

# v1.2

### 1.2.0
  * PMD & CPD compatible with Gradle v2.3+. It resolves dependencies and tool versions according
   to the used gradle version.
