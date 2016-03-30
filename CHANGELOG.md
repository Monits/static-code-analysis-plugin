## History

# v1.6
* **1.6.3**
 * Works with Gradle 2.10 or later as intended 

* **1.6.2**
 * Botched release. Don't use.

* **1.6.1**
 * Update Checkstyle to 6.15
 * Update fb-contrib to 6.6.0

* **1.6.0**
 * PMD / Findbugs ignore changes to resources in module dependencies, just as it does for it's own.
 * Custom jars with Android lint rules can be specified as dependencies under `androidLint`

# v1.5

* **1.5.16**
  * Make sure Monits' repository is added to resolve dependencies
  * Upgrade to Checkstyle 6.14.1

* **1.5.15**
  * CPD no longer fails when no java files are found

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
