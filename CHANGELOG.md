## What's new:

* Added a very simple config to change the default pattern.
* * Supported args are:
  * `{class}` - The full class name.
  * `{method}` - The calling method.
  * `{simpleClass}` - The simple class name. (without the package)
  * `{methodParams}` - Simple method parameter array.
  * `{methodReturnType}` - The return type of the method.
* The new default pattern is `[{simpleClass}#{method}] {message}`
* * The old one is `[{class}#{method}] {message}`
* Fixed issues on Connector.
* Updated Dark Matter