# Foam Dart Battle - Android App

An Android application built with Kotlin and Jetpack Compose that allows users to create custom geofenced "battle zones" for foam dart battles using Google Maps. 

The app includes a roadmap for future integration of Augmented Reality (ARCore) as a heads-up display and Machine Learning (ML Kit/CameraX) to automatically track and score dart hits.

## Features

- **Interactive Maps:** Built using `@googlemaps/android-maps-compose`.
- **Custom Geofences:** Tap points on the map to draw a custom polygon boundary for your battle zone.
- **Geofence Monitoring:** Automatically tracks when a player leaves or enters the defined battle zone and triggers a broadcast event.
- **Modern Architecture:** Uses Jetpack Compose for declarative UI.

## Setup & Installation

To run this project, you will need a Google Maps Platform API Key.

1. **Clone the repository:**
   ```bash
   git clone <your-repo-url>
   cd foam-dart-battle
   ```

2. **Add your Maps API Key:**
   This project securely injects the API key from a `local.properties` file to ensure it's not accidentally committed to version control.
   
   Create a file named `local.properties` in the root of the project (if it doesn't already exist) and add your API key:
   ```properties
   MAPS_API_KEY=your_api_key_here
   ```
   *(Note: You can get a free Maps Demo Key for prototyping or generate a production key from the Google Cloud Console).*

3. **Open in Android Studio:**
   Open the project directory in Android Studio. Gradle will automatically sync and pull in the necessary dependencies.

4. **Build and Run:**
   Build the project and deploy it to a physical Android device or an emulator with Google Play Services installed.

## License

This project is open-sourced under the MIT License. See the [LICENSE](LICENSE) file for details.

*Usage of Google Maps Platform products and services within this application is subject to the Google Maps Platform Terms of Service.*
