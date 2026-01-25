package cz.svitaninymburk.projects.reservations

import dev.kilua.Hot
import dev.kilua.utils.unsafeCast
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Image
import kotlin.js.json
import kotlin.js.unsafeCast
import web.blob.Blob
import web.blob.BlobPropertyBag
import web.file.File
import web.file.FilePropertyBag
import web.url.URL

actual fun bundlerHot(): Hot? {
    return js("import.meta.webpackHot").unsafeCast<Hot?>() ?: js("import.meta.hot").unsafeCast<Hot?>()
}

actual fun copyToClipboard(text: String) {
    val nav = window.navigator.asDynamic()
    if (nav.clipboard != undefined) {
        nav.clipboard.writeText(text)
    } else {
        console.warn("Clipboard API nedostupné")
    }
}

actual fun shareQrImage(imageUrl: String) {
// 1. Stáhneme obrázek pomocí window.fetch (standardní API)
    window.fetch(imageUrl).then { response ->
        response.blob()
    }.then { blob ->
        val file = File(
            arrayOf(blob),
            "qr_platba.png",
            json("type" to "image/png").unsafeCast<FilePropertyBag>()
        )

        val shareData = json(
            "title" to "QR Platba",
            "text" to "QR kód pro platbu rezervace",
            "files" to arrayOf(file)
        )

        val navigator = window.navigator.asDynamic()

        val canShare = navigator.canShare != undefined && navigator.canShare(shareData) == true

        if (canShare) {
            navigator.share(shareData).catch { err ->
                console.warn("Sdílení zrušeno uživatelem nebo selhalo", err)
            }
        } else {
            console.log("Web Share API nepodporováno, otevírám obrázek.")
            window.open(imageUrl, "_blank")
        }

        null // Return pro Promise chain
    }.catch { err ->
        console.error("Chyba při stahování/sdílení QR kódu", err)
        null
    }
}

actual fun shareSvgAsPng(svgString: String) {
    // 1. Vytvoříme Blob z SVG řetězce (aby se choval jako soubor)
    // Důležité: type "image/svg+xml;charset=utf-8" řeší diakritiku
    val svgBlob = Blob(
        arrayOf(svgString),
        json("type" to "image/svg+xml;charset=utf-8").unsafeCast<BlobPropertyBag>()
    )

    // 2. Vytvoříme virtuální URL pro tento Blob
    val url = URL.createObjectURL(svgBlob)

    // 3. Načteme SVG do HTML Image objektu
    val img = Image()

    img.onload = {
        // Až se obrázek načte (je to asynchronní), vykreslíme ho na Canvas
        val canvas = document.createElement("canvas") as HTMLCanvasElement

        // Nastavíme velikost plátna (klidně větší pro lepší kvalitu)
        canvas.width = 1000
        canvas.height = 1000

        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D

        // Bílé pozadí (pro jistotu, kdyby banka neuměla průhlednost)
        ctx.fillStyle = "#FFFFFF"
        ctx.fillRect(0.0, 0.0, 1000.0, 1000.0)

        // Vykreslení SVG na plátno
        ctx.drawImage(img, 0.0, 0.0, 1000.0, 1000.0)

        // Uvolníme paměť URL
        URL.revokeObjectURL(url)

        // 4. Export Canvasu do PNG Blobu
        // Použijeme asDynamic, protože callback ve Wasm/JS se liší signaturou
        canvas.asDynamic().toBlob({ blob: Blob? ->
            if (blob != null) {
                shareBlob(blob)
            } else {
                console.error("Nepodařilo se vytvořit PNG z Canvasu")
            }
        }, "image/png")

        Unit // Return pro onload
    }

    img.src = url
}

/**
 * Privátní pomocná funkce pro samotné sdílení Blobu (už hotového PNG)
 */
actual fun shareBlob(blob: Blob) {
    val file = File(
        arrayOf(blob),
        "qr_platba.png",
        json("type" to "image/png").unsafeCast<FilePropertyBag>()
    )

    val shareData = json(
        "title" to "QR Platba",
        "text" to "QR kód pro platbu rezervace",
        "files" to arrayOf(file)
    )

    val navigator = window.navigator.asDynamic()
    val canShare = navigator.canShare != undefined && navigator.canShare(shareData) == true

    if (canShare) {
        navigator.share(shareData).catch { err ->
            console.warn("Sdílení zrušeno", err)
        }
    } else {
        val downloadUrl = URL.createObjectURL(file)
        val a = document.createElement("a")
        a.setAttribute("href", downloadUrl)
        a.setAttribute("download", "qr_platba.png")
        a.asDynamic().click()
        URL.revokeObjectURL(downloadUrl)
    }
}