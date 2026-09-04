import Foundation

/// Cliente HTTP compartido por todas las fuentes de `Schedule/Sources` y
/// `Standings/Sources` — equivalente de `StandingsHttpClient`/`StandingsMoshi` en
/// Android, pero compartido entre los dos módulos en vez de duplicado (Android solo lo
/// declaraba dentro de `standings`; `schedule` construye su propio `Request` a mano en
/// cada fuente, pero apunta a la misma necesidad).
///
/// Mapeo de timeouts (ver el comentario original en `StandingsHttpClient.kt` sobre por
/// qué OkHttp necesita tres timeouts distintos): `URLSession` no separa "tiempo de
/// conexión" de "tiempo de lectura" — `timeoutIntervalForRequest` ya cubre ambos (si no
/// llega NI UN BYTE en ese plazo, falla), así que hace las veces de `connectTimeout` +
/// `readTimeout` juntos. `timeoutIntervalForResource` es el tope duro de la operación
/// completa (DNS + conexión + redirecciones + cuerpo entero) — el mismo rol que
/// `callTimeout` en OkHttp, y lo que garantiza que una fuente atascada falle sola en vez
/// de dejar colgada la sincronización de las demás.
public enum HTTPClient {

    public static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 PitBoard/1.0"

    public struct RequestError: LocalizedError {
        public let url: String
        public let statusCode: Int?
        public let underlying: String?

        public var errorDescription: String? {
            if let statusCode {
                return "\(url): HTTP \(statusCode)"
            }
            return "\(url): \(underlying ?? "cuerpo vacío")"
        }
    }

    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 30
        configuration.httpAdditionalHeaders = ["User-Agent": userAgent]
        return URLSession(configuration: configuration)
    }()

    /// GET y devuelve el cuerpo como String (UTF-8) — equivalente del patrón
    /// `Request.Builder().url(url)...execute()` repetido en cada fuente de Android.
    public static func fetchHTML(_ urlString: String, referer: String? = nil) async throws -> String {
        guard let url = URL(string: urlString) else {
            throw RequestError(url: urlString, statusCode: nil, underlying: "URL inválida")
        }
        var request = URLRequest(url: url)
        if let referer {
            request.setValue(referer, forHTTPHeaderField: "Referer")
        }
        let (data, response) = try await session.data(for: request)
        try validate(response, urlString: urlString)
        guard let text = String(data: data, encoding: .utf8) else {
            throw RequestError(url: urlString, statusCode: nil, underlying: "cuerpo no es UTF-8 válido")
        }
        return text
    }

    /// GET y decodifica JSON con `Codable` — equivalente de Moshi + `KotlinJsonAdapterFactory`.
    public static func fetchJSON<T: Decodable>(_ urlString: String, as type: T.Type = T.self) async throws -> T {
        let json = try await fetchHTML(urlString)
        guard let data = json.data(using: .utf8) else {
            throw RequestError(url: urlString, statusCode: nil, underlying: "cuerpo no es UTF-8 válido")
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    /// POST `application/x-www-form-urlencoded` — usado solo por el AJAX de
    /// `ImsaStandingsSource` (equivalente de `FormBody.Builder()` en OkHttp).
    public static func postForm(
        _ urlString: String,
        fields: [String: String],
        referer: String? = nil
    ) async throws -> String {
        guard let url = URL(string: urlString) else {
            throw RequestError(url: urlString, statusCode: nil, underlying: "URL inválida")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        if let referer {
            request.setValue(referer, forHTTPHeaderField: "Referer")
        }
        let body = fields
            .map { key, value in
                let encodedKey = key.addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) ?? key
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) ?? value
                return "\(encodedKey)=\(encodedValue)"
            }
            .joined(separator: "&")
        request.httpBody = body.data(using: .utf8)

        let (data, response) = try await session.data(for: request)
        try validate(response, urlString: urlString)
        guard let text = String(data: data, encoding: .utf8) else {
            throw RequestError(url: urlString, statusCode: nil, underlying: "cuerpo no es UTF-8 válido")
        }
        return text
    }

    /// GET de bytes crudos (fotos de piloto, logos de equipo/categoría) — usa el MISMO
    /// `URLSession` que el resto del cliente, con el User-Agent de navegador ya aplicado
    /// vía `httpAdditionalHeaders`. Existe porque `AsyncImage` nativo de SwiftUI no expone
    /// forma de mandar cabeceras propias, y varias fuentes de fotos (Wikimedia sobre todo)
    /// devuelven 403 sin un User-Agent que parezca un navegador real — el mismo problema
    /// que forzó `PitBoardApplication.newImageLoader()` en Android (Coil con OkHttp por
    /// defecto también se llevaba un 403 de Wikimedia). Ver `RemoteImage.swift`.
    public static func fetchImageData(_ urlString: String) async throws -> Data {
        guard let url = URL(string: urlString) else {
            throw RequestError(url: urlString, statusCode: nil, underlying: "URL inválida")
        }
        let (data, response) = try await session.data(from: url)
        try validate(response, urlString: urlString)
        guard !data.isEmpty else {
            throw RequestError(url: urlString, statusCode: nil, underlying: "cuerpo vacío")
        }
        return data
    }

    private static func validate(_ response: URLResponse, urlString: String) throws {
        guard let http = response as? HTTPURLResponse else { return }
        guard (200...299).contains(http.statusCode) else {
            throw RequestError(url: urlString, statusCode: http.statusCode, underlying: nil)
        }
    }
}

private extension CharacterSet {
    static let urlQueryValueAllowed: CharacterSet = {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return allowed
    }()
}
