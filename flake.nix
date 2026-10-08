{
  description = "Tick: Android client for Kimai";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachSystem [ "x86_64-linux" "aarch64-linux" ] (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        # Keep in sync with compileSdk / buildToolsVersion in app/build.gradle.kts
        platformVersion = "35";
        buildToolsVersion = "35.0.0";

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ platformVersion ];
          buildToolsVersions = [ buildToolsVersion ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis_playstore" ];
          abiVersions = [ "x86_64" ];
          includeNDK = false;
        };

        sdkRoot = "${androidComposition.androidsdk}/libexec/android-sdk";
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.jdk17
            pkgs.android-tools # adb
            androidComposition.androidsdk
          ];

          JAVA_HOME = "${pkgs.jdk17}";
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;

          # AGP downloads its own aapt2 from Maven. That binary is dynamically
          # linked and does not run on NixOS, so point Gradle at the Nix one.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";

          shellHook = ''
            echo "Tick dev shell."
            echo "With direnv (PATH_add scripts), or by calling scripts/tick directly:"
            scripts/tick help
          '';
        };
      });
}
