# Alexa Auto Login with Amazon Authorization
This package provides an implementation of `AuthController` defined in the `alexa-auto-apis` package. The `AuthController` implementation uses the Login with Amazon (LWA) authorization service provided by the Alexa Auto SDK.

The `AuthController` implementation is useful for observing changes in the authentication state (indicated by the Boolean value of `loggedIn`).

The `AuthController` implementation also provides the workflow for starting authentication. After the user installs an app on an Alexa-enabled device for the first time or when the logged-off user tries to log in again, the authentication process starts. The workflow provided by `AuthController` supports LWA using code-based linking (CBL) as the authorization method. The CBL code obtained through the authentication workflow is displayed on the head unit device so that the user can enter the CBL code on their mobile device to authenticate the head unit device.