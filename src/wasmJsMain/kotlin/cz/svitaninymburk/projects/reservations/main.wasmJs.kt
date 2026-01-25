package cz.svitaninymburk.projects.reservations

import dev.kilua.Hot

actual fun bundlerHot(): Hot? {
    return null
}

@JsFun("(text) => navigator.clipboard.writeText(text)")
private external fun writeTextToClipboardJs(text: String)

actual fun copyToClipboard(text: String) {
    writeTextToClipboardJs(text)
}

@JsFun("""(url) => {
    fetch(url)
        .then(response => response.blob())
        .then(blob => {
            var file = new File([blob], "qr_platba.png", { type: "image/png" });
            var data = {
                title: 'QR Platba',
                text: 'QR kód pro platbu rezervace',
                files: [file]
            };
            
            if (navigator.canShare && navigator.canShare(data)) {
                navigator.share(data);
            } else {
                window.open(url, '_blank');
            }
        })
        .catch(err => console.error('Share failed', err));
}""")
private external fun shareQrImageJs(url: String)

actual fun shareQrImage(imageUrl: String) {
    shareQrImageJs(imageUrl)
}

@JsFun("""(svgString) => {
    // 1. Vytvoření Blob objektu z SVG stringu
    var blob = new Blob([svgString], {type: "image/svg+xml;charset=utf-8"});
    var url = URL.createObjectURL(blob);
    
    // 2. Načtení do obrázku
    var img = new Image();
    img.onload = function() {
        // 3. Vykreslení na Canvas
        var canvas = document.createElement("canvas");
        canvas.width = 1000;
        canvas.height = 1000;
        var ctx = canvas.getContext("2d");
        
        // Bílé pozadí (nutné pro transparentní SVG, aby byl kód čitelný)
        ctx.fillStyle = "#FFFFFF";
        ctx.fillRect(0, 0, 1000, 1000);
        
        // Vykreslení SVG
        ctx.drawImage(img, 0, 0, 1000, 1000);
        URL.revokeObjectURL(url);
        
        // 4. Konverze na PNG Blob
        canvas.toBlob(function(pngBlob) {
            if (!pngBlob) return;
            
            // 5. Příprava souboru pro sdílení
            var file = new File([pngBlob], "qr_platba.png", { type: "image/png" });
            var data = {
                files: [file],
                title: "QR Platba",
                text: "QR kód pro platbu rezervace"
            };
            
            // 6. Sdílení nebo Stažení (Fallback)
            if (navigator.canShare && navigator.canShare(data)) {
                navigator.share(data).catch(err => console.warn("Share cancelled", err));
            } else {
                // Fallback: Stáhnout jako soubor
                var a = document.createElement("a");
                a.href = URL.createObjectURL(file);
                a.download = "qr_platba.png";
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
            }
        }, "image/png");
    };
    img.onerror = function(e) {
        console.error("Chyba při načítání SVG pro sdílení", e);
    };
    img.src = url;
}""")
private external fun shareSvgAsPngJs(svgString: String)

actual fun shareSvgAsPng(svgString: String) {
    shareSvgAsPngJs(svgString)
}
