package cz.svitaninymburk.projects.reservations

import web.blob.Blob

expect fun copyToClipboard(text: String)

expect fun shareQrImage(imageUrl: String)

expect fun shareSvgAsPng(svgString: String)

expect fun shareBlob(blob: Blob)
