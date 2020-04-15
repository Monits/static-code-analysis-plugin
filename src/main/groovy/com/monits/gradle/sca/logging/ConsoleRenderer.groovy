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
package com.monits.gradle.sca.logging

import groovy.transform.CompileStatic
import org.gradle.api.GradleException

/**
 * Renders information in a format suitable for logging to the console.
 *
 * This class is copied from org.gradle.internal.logging.ConsoleRenderer since in 2.14 it was moved
 * to an internal package breaking backwards compatibility. Minor modifications where done to comply with codenarc.
 */
@CompileStatic
class ConsoleRenderer {
    /**
     * Renders a path name as a file URL that is likely recognized by consoles.
     *
     * @param path The file to which to link.
     * @return A string containing the path to the file in such a format that consoles will allow users to click them.
     */
    String asClickableFileUrl(final File path) {
        // File.toURI().toString() leads to an URL like this on Mac: file:/reports/index.html
        // This URL is not recognized by the Mac console (too few leading slashes). We solve
        // this be creating an URI with an empty authority.
        try {
            new URI('file', '', path.toURI().path, null, null).toString()
        } catch (URISyntaxException e) {
            throw new GradleException('Invalid file path', e)
        }
    }
}
