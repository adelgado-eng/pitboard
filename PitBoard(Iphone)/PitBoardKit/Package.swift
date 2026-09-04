// swift-tools-version:5.10
import PackageDescription

let package = Package(
    name: "PitBoardKit",
    platforms: [
        .iOS(.v17)
    ],
    products: [
        .library(name: "PitBoardKit", targets: ["PitBoardKit"])
    ],
    dependencies: [
        // Equivalente de Jsoup (usado en 15+ fuentes de standings/schedule en Android).
        .package(url: "https://github.com/scinfu/SwiftSoup.git", from: "2.7.0")
    ],
    targets: [
        .target(
            name: "PitBoardKit",
            dependencies: ["SwiftSoup"]
        ),
        .testTarget(
            name: "PitBoardKitTests",
            dependencies: ["PitBoardKit"]
        )
    ]
)
