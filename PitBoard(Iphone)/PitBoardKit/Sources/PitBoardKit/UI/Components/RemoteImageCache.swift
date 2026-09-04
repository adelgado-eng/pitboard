import UIKit

/// Caché en memoria de imágenes ya descargadas — equivalente ligero del caché de Coil en
/// Android. `NSCache` se vacía sola bajo presión de memoria y es segura para acceso
/// concurrente sin bloqueo adicional, así que no hace falta un actor propio encima.
final class RemoteImageCache: @unchecked Sendable {
    static let shared = RemoteImageCache()

    private let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 300
        return cache
    }()

    private init() {}

    func image(for key: String) -> UIImage? {
        cache.object(forKey: key as NSString)
    }

    func store(_ image: UIImage, for key: String) {
        cache.setObject(image, forKey: key as NSString)
    }
}
