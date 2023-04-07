package xyz.wagyourtail.unimined.api.output.jar

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.output.Output

abstract interface JarOutput: Output<Jar> {

}
