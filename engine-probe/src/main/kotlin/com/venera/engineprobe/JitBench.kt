package com.venera.engineprobe

import java.io.File
import kotlin.system.measureTimeMillis

/**
 * 给"桌面运行期 JDK 锁哪一版"这条拍板一个**实测差值**。
 *
 * 起因：GraalJS 在 JDK 21/23 上自报跑的是 **fallback 解释器**（要 ≥25 且 <26 才有 Truffle JIT），
 * 所以 S0-3 那批"5–10 秒返回"的读数全是无 JIT 档位。差多少？量三笔：
 * 1. 纯 JS 计算（`fib(27)`）—— 引擎本行的差距，最敏感；
 * 2. `JSON` 序列化/解析 —— 桥协议每笔调用都走它；
 * 3. 真源 `jm` 的 explore 端到端 —— 含网络，差距被稀释后的"用户实际感受"上限。
 *
 * 跑法：`./gradlew :engine-probe:bench -Pjdk=21` / `-Pjdk=25`
 */
fun main(args: Array<String>) {
    val root = File(System.getProperty("user.dir"))
    val assetDir = File(root, "app/src/main/assets")
    val dataDir = File(System.getProperty("java.io.tmpdir"), "venera-bench")
    val proxy = args.firstOrNull { it.startsWith("--proxy=") }?.removePrefix("--proxy=")

    println("BENCH jdk=${System.getProperty("java.version")} vm=${System.getProperty("java.vm.name")}")

    EngineSession(DesktopJsHost(assetDir, dataDir, proxy), assetDir).use { s ->
        val host = s.host
        val loadMs = measureTimeMillis { s.load("jm") }
        println("BENCH 装载jm=${loadMs}ms")

        host.exec("function __fib(n){ return n < 2 ? n : __fib(n-1) + __fib(n-2); }")
        val warm = measureTimeMillis { repeat(3) { host.evaluate("__fib(24)") } }
        val fibMs = measureTimeMillis { repeat(10) { host.evaluate("__fib(26)") } }
        println("BENCH fib26x10=${fibMs}ms（预热 fib24x3=${warm}ms）")

        val jsonMs = measureTimeMillis {
            host.evaluate(
                "for (var i=0;i<2000;i++){ JSON.stringify({a:i,b:'x'.repeat(32),c:[1,2,3]}); " +
                    "JSON.parse('{\"k\":[1,2,3,4,5,6,7,8]}'); }"
            )
        }
        println("BENCH json 4000 次=${jsonMs}ms")

        val netMs = measureTimeMillis {
            runCatching { s.explore(1) }.onFailure { println("BENCH explore FAIL ${it.message?.take(120)}") }
        }
        println("BENCH explore(jm,含网络)=${netMs}ms")
    }
    println("BENCH_DONE")
}
