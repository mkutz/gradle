/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.cpp

import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputDirectory

class CompileCpp extends SourceTask {

    CppCompiler compiler
    
    def destinationDir
    def output

    CompileCpp() {
        compiler = new GppCppCompiler()
    }

    @OutputDirectory
    File getDestinationDir() {
        project.file(destinationDir)
    }

    @OutputFile
    File getOutput() {
        project.file(output)
    }

    @TaskAction
    void compile() {
        compiler.source = getSource()
        compiler.destinationDir = getDestinationDir()
        compiler.output = getOutput()
        compiler.execute()
    }

}