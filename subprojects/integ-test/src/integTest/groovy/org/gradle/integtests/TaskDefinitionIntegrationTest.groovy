/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@SuppressWarnings('IntegrationTestFixtures')
class TaskDefinitionIntegrationTest extends AbstractIntegrationSpec {
    private static final String CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS = """
        import javax.inject.Inject

        class CustomTask extends DefaultTask {
            final String message
            final int number

            @Inject
            CustomTask(String message, int number) {
                this.message = message
                this.number = number
            }

            @TaskAction
            void printIt() {
                println("\$message \$number")
            }
        }
    """

    def "can define tasks using task keyword and identifier"() {
        buildFile << """
            task nothing
            task withAction << { }
            task emptyOptions()
            task task
            task withOptions(dependsOn: [nothing, withAction, emptyOptions, task])
            task withOptionsAndAction(dependsOn: withOptions) << { }
        """

        expect:
        executer.expectDeprecationWarning()
        succeeds ":emptyOptions", ":nothing", ":task", ":withAction", ":withOptions", ":withOptionsAndAction"
    }

    def "can define tasks using task keyword and GString"() {
        buildFile << """
            ext.v = 'Task'
            task "nothing\$v"
            task "withAction\$v" << { }
            task "emptyOptions\$v"()
            task "withOptions\$v"(dependsOn: [nothingTask, withActionTask, emptyOptionsTask])
            task "withOptionsAndAction\$v"(dependsOn: withOptionsTask) << { }
        """

        expect:
        executer.expectDeprecationWarning()
        succeeds ":emptyOptionsTask", ":nothingTask", ":withActionTask", ":withOptionsTask", ":withOptionsAndActionTask"
    }

    def "can define tasks using task keyword and String"() {
        buildFile << """
            task 'nothing'
            task 'withAction' << { }
            task 'emptyOptions'()
            task 'withOptions'(dependsOn: [nothing, withAction, emptyOptions])
            task 'withOptionsAndAction'(dependsOn: withOptions) << { }
        """

        expect:
        executer.expectDeprecationWarning()
        succeeds ":emptyOptions", ":nothing",":withAction", ":withOptions", ":withOptionsAndAction"
    }

    def "can define tasks in nested blocks"() {
        buildFile << """
            2.times { task "dynamic\$it" }
            if (dynamic0) { task inBlock }
            def task() { task inMethod }
            task()
            def cl = { -> task inClosure }
            cl()
            task all(dependsOn: [dynamic0, dynamic1, inBlock, inMethod, inClosure])
        """

        expect:
        succeeds ":dynamic0", ":dynamic1", ":inBlock", ":inClosure", ":inMethod", ":all"
    }

    def "can define tasks using task method expression"() {
        buildFile << """
            ext.a = 'a' == 'b' ? null: task(withAction) << { }
            a = task(nothing)
            a = task(emptyOptions())
            ext.taskName = 'dynamic'
            a = task("\$taskName") << { }
            a = task('string')
            a = task('stringWithAction') << { }
            a = task('stringWithOptions', description: 'description')
            a = task('stringWithOptionsAndAction', description: 'description') << { }
            a = task(withOptions, description: 'description')
            a = task(withOptionsAndAction, description: 'description') << { }
            a = task(anotherWithAction).doFirst\n{}
            task all(dependsOn: tasks as List)
        """

        expect:
        executer.expectDeprecationWarning()
        succeeds ":anotherWithAction", ":dynamic", ":emptyOptions",
            ":nothing", ":string", ":stringWithAction", ":stringWithOptions", ":stringWithOptionsAndAction",
            ":withAction", ":withOptions", ":withOptionsAndAction", ":all"
    }

    def "can configure tasks when the are defined"() {
        buildFile << """
            task withDescription { description = 'value' }
            task(asMethod)\n{ description = 'value' }
            task asStatement(type: TestTask) { property = 'value' }
            task "dynamic"(type: TestTask) { property = 'value' }
            ext.v = task(asExpression, type: TestTask) { property = 'value' }
            task(postConfigure, type: TestTask).configure { property = 'value' }
            [asStatement, dynamic, asExpression, postConfigure].each {
                assert 'value' == it.property
            }
            [withDescription, asMethod].each {
                assert 'value' == it.description
            }
            task all(dependsOn: tasks as List)
            class TestTask extends DefaultTask { String property }
        """

        expect:
        succeeds "all"
    }

    def "does not hide local methods and variables"() {
        buildFile << """
            String name = 'a'; task name
            def taskNameMethod(String name = 'c') { name }
            task taskNameMethod('d')
            def addTaskMethod(String methodParam) { task methodParam }
            addTaskMethod('e')
            def addTaskWithClosure(String methodParam) { task(methodParam) { ext.property = 'value' } }
            addTaskWithClosure('f')
            def addTaskWithMap(String methodParam) { task(methodParam, description: 'description') }
            addTaskWithMap('g')
            ext.cl = { String taskNameParam -> task taskNameParam }
            cl.call('h')
            cl = { String taskNameParam -> task(taskNameParam) { ext.property = 'value' } }
            cl.call('i')
            assert 'value' == f.property
            assert 'value' == i.property
            task all(dependsOn: tasks as List)
        """

        expect:
        succeeds ":a", ":d", ":e", ":f", ":g", ":h", ":i", ":all"
    }

    def "unsupported task parameter fails with decent error message"() {
        buildFile << "task a(Type:Copy)"
        when:
        fails 'a'
        then:
        failure.assertHasCause("Could not create task 'a': Unknown argument(s) in task definition: [Type]")
    }

    def "renders deprecation message when using left shift operator to define action"() {
        given:
        String taskName = 'helloWorld'
        String message = 'Hello world!'

        buildFile << """
            task $taskName << {
                println '$message'
            }
        """

        when:
        executer.expectDeprecationWarning()
        succeeds taskName

        then:
        output.contains(message)
        output.contains("The Task.leftShift(Closure) method has been deprecated and is scheduled to be removed in Gradle 5.0. Please use Task.doLast(Action) instead.")
    }

    def "can construct a custom task without constructor arguments"() {
        given:
        buildFile << """
            class CustomTask extends DefaultTask {
                @TaskAction
                void printIt() {
                    println("hello world")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        result.output.contains('hello world')
    }

    def "can construct a custom task with constructor arguments"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.create('myTask', CustomTask, 'hello', 42)"

        when:
        run 'myTask'

        then:
        result.output.contains("hello 42")
    }

    @Unroll
    def "can construct a custom task with constructor arguments as #description via Map"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "task myTask(type: CustomTask, constructorArgs: ${constructorArgs})"

        when:
        run 'myTask'

        then:
        result.output.contains("hello 42")

        where:
        description | constructorArgs
        'List'      | "['hello', 42]"
        'Object[]'  | "(['hello', 42] as Object[])"
    }

    @Unroll
    def "fails to create custom task using #description if constructor arguments are missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        result.output.contains("java.lang.IllegalArgumentException: Unable to determine CustomTask_Decorated argument #2: missing parameter value of type int, or no service of type int")

        where:
        description   | script
        'Map'         | "task myTask(type: CustomTask, constructorArgs: ['hello'])"
        'direct call' | "tasks.create('myTask', CustomTask, 'hello')"
    }

    @Unroll
    def "fails to create custom task using #description if all constructor arguments missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        result.output.contains("java.lang.IllegalArgumentException: Unable to determine CustomTask_Decorated argument #1: missing parameter value of type class java.lang.String, or no service of type class java.lang.String")

        where:
        description   | script
        'Map'         | "task myTask(type: CustomTask)"
        'Map (null)'  | "task myTask(type: CustomTask, constructorArgs: null)"
        'direct call' | "tasks.create('myTask', CustomTask)"
    }

    @Unroll
    def "fails when constructorArgs not list or Object[], but #description"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "task myTask(type: CustomTask, constructorArgs: ${constructorArgs})"

        when:
        fails 'myTask'

        then:
        result.output.contains("constructorArgs must be a List or Object[]")

        where:
        description | constructorArgs
        'Set'       | '[1, 2, 1] as Set'
        'Map'       | '[a: 1, b: 2]'
        'String'    | '"abc"'
        'primitive' | '123'
    }

    @Unroll
    def "fails when #description constructor argument is wrong type"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.create('myTask', CustomTask, $constructorArgs)"

        when:
        fails 'myTask'

        then:
        result.output.contains("java.lang.IllegalArgumentException: Unable to determine CustomTask_Decorated argument #$argumentNumber: value 123 not assignable to type $outputType, or no service of type $outputType")

        where:
        description | constructorArgs | argumentNumber | outputType
        'first'     | '123, 234'      | 1              | 'class java.lang.String'
        'last'      | '"abc", "123"'  | 2              | 'int'
    }

    @Unroll
    def "fails to create via #description when null passed as a constructor argument value at #position"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        result.output.contains("java.lang.NullPointerException: Received null for CustomTask constructor argument #$position")

        where:
        description   | position | script
        'Map'         | 1        | "task myTask(type: CustomTask, constructorArgs: [null, 1])"
        'direct call' | 1        | "tasks.create('myTask', CustomTask, null, 1)"
        'Map'         | 2        | "task myTask(type: CustomTask, constructorArgs: ['abc', null])"
        'direct call' | 2        | "tasks.create('myTask', CustomTask, 'abc', null)"
    }

    def "can construct a task with @Inject services"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final WorkerExecutor executor

                @Inject
                CustomTask(WorkerExecutor executor) {
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it" : "NOT IT")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it")
    }

    def "can construct a task with @Inject services and constructor args"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final int number
                private final WorkerExecutor executor

                @Inject
                CustomTask(int number, WorkerExecutor executor) {
                    this.number = number
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it \$number" : "\$number NOT IT")
                }
            }

            tasks.create('myTask', CustomTask, 15)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it 15")
    }

    @Requires(KOTLIN_SCRIPT)
    def 'can run custom task with constructor arguments via Kotlin friendly DSL'() {
        given:
        settingsFile << "rootProject.buildFileName = 'build.gradle.kts'"
        file("build.gradle.kts") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val message: String, private val number: Int) : DefaultTask() {
                @TaskAction fun run() = println("\$message \$number")
            }

            tasks.create<CustomTask>("myTask", "hello", 42)
        """

        when:
        run 'myTask'

        then:
        result.output.contains('hello 42')
    }

    @Requires(KOTLIN_SCRIPT)
    def "can construct a task in Kotlin with @Inject services"() {
        given:
        settingsFile << "rootProject.buildFileName = 'build.gradle.kts'"
        file("build.gradle.kts") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val executor: WorkerExecutor) : DefaultTask() {
                @TaskAction fun run() = println(if (executor != null) "got it" else "NOT IT")
            }

            tasks.create<CustomTask>("myTask")
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it")
    }

    @Requires(KOTLIN_SCRIPT)
    def "can construct a task with @Inject services and constructor args via Kotlin friendly DSL"() {
        given:
        settingsFile << "rootProject.buildFileName = 'build.gradle.kts'"
        file("build.gradle.kts") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val number: Int, private val executor: WorkerExecutor) : DefaultTask() {
                @TaskAction fun run() = println(if (executor != null) "got it \$number" else "\$number NOT IT")
            }

            tasks.create<CustomTask>("myTask", 15)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it 15")
    }
}
