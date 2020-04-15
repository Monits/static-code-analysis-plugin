/*
 * Copyright 2010-2020 Monits S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.monits.gradle.sca.performance.io

import groovy.transform.CompileStatic
import org.hamcrest.Matcher

import static org.junit.Assert.assertThat

/**
 * A File with useful utilities to assist unit testing.
*/
@CompileStatic
class TestFile extends File {
    TestFile(final String pathname) {
        super(pathname)
    }

    TestFile(final String parent, final String child) {
        super(parent, child)
    }

    TestFile(final File parent, final String child) {
        super(parent, child)
    }

    TestFile(final URI uri) {
        super(uri)
    }

    TestFile(final File file) {
        super(file.absolutePath)
    }

    TestFile assertContents(Matcher<String> matcher) {
        assertThat(text, matcher)
        this
    }
}
