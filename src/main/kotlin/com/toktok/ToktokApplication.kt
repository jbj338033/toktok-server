package com.toktok

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ToktokApplication

fun main(args: Array<String>) {
    runApplication<ToktokApplication>(*args)
}
