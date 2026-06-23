package nz.eloque.quits.sqlite

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

/** A SQLite driver backed by [worker.js] (SQLite-WASM + OPFS) for Room on the browser. */
fun createWebSqliteDriver(): WebWorkerSQLiteDriver = WebWorkerSQLiteDriver(jsWorker())

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsWorker(): Worker = js("""new Worker(new URL("quits-sqlite-worker/worker.js", import.meta.url))""")
