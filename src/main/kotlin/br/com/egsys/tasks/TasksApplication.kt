package br.com.egsys.tasks

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TasksApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<TasksApplication>(*args)
}
