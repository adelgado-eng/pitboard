import SwiftUI

/// Mismos 3 casos que `AsyncImagePhase` (SwiftUI) — se define uno propio en vez de
/// reutilizar el de Apple porque `RemoteImage` no es un `AsyncImage` por dentro.
public enum RemoteImagePhase {
    case empty
    case success(Image)
    case failure(Error)
}

/// Sustituto de `AsyncImage` que manda el `User-Agent` de navegador de `HTTPClient` en
/// cada petición — necesario porque `AsyncImage` nativo no expone forma de añadir
/// cabeceras, y varias fuentes de foto/logo (Wikimedia en particular) devuelven 403 sin
/// un User-Agent que parezca un navegador real. Equivalente del
/// `OkHttpClient` con interceptor de User-Agent que `PitBoardApplication.newImageLoader()`
/// le pasaba a Coil en Android.
///
/// LIMITACIÓN CONOCIDA (no resuelta en este pase): no rasteriza SVG. Los logos de equipo
/// de Fórmula E son SVG (ver `FormulaEStandingsSource.swift` y el comentario original en
/// `PitBoardApplication.kt` sobre `SvgDecoder.Factory()` de Coil) — `UIImage(data:)` no
/// sabe decodificarlos, así que esas filas concretas caen al `.failure` y se ven con el
/// icono de respaldo en vez del logo real. Arreglarlo bien pediría añadir una dependencia
/// de rasterizado SVG (ej. SwiftDraw) al `Package.swift` — se deja fuera a propósito
/// porque no se puede verificar su API exacta sin compilador en esta sesión; mejor un
/// icono de respaldo honesto que una llamada a una API de terceros sin comprobar.
public struct RemoteImage<Content: View>: View {
    private let urlString: String?
    private let content: (RemoteImagePhase) -> Content

    @State private var phase: RemoteImagePhase = .empty

    public init(urlString: String?, @ViewBuilder content: @escaping (RemoteImagePhase) -> Content) {
        self.urlString = urlString
        self.content = content
    }

    public var body: some View {
        content(phase)
            .task(id: urlString) {
                await load()
            }
    }

    private func load() async {
        guard let urlString, !urlString.isEmpty else {
            phase = .empty
            return
        }

        if let cached = RemoteImageCache.shared.image(for: urlString) {
            phase = .success(Image(uiImage: cached))
            return
        }

        do {
            let data = try await HTTPClient.fetchImageData(urlString)
            guard let uiImage = UIImage(data: data) else {
                throw HTTPClient.RequestError(url: urlString, statusCode: nil, underlying: "formato de imagen no soportado (¿SVG?)")
            }
            RemoteImageCache.shared.store(uiImage, for: urlString)
            if !Task.isCancelled {
                phase = .success(Image(uiImage: uiImage))
            }
        } catch {
            if !Task.isCancelled {
                phase = .failure(error)
            }
        }
    }
}
