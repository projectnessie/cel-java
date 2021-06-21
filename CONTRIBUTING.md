# Contributing to Nessie
## How to contribute
Everyone is encouraged to contribute to the CEL-Java project. We welcome of course code changes, 
but we are also grateful for bug reports, feature suggestions, helping with testing and 
documentation, or simply spreading the word about CEL-Java and [projectnessie](https://github.com/projectnessie/).

Please use [GitHub issues](https://github.com/projectnessie/cel-java/issues) for bug reports and
feature requests and [GitHub Pull Requests](https://github.com/projectnessie/cel-java/pulls) for code
contributions.

More information are available at https://projectnessie.org/develop/

## Code of conduct
You must agree to abide by the Project Nessie [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting issues
Issues can be filed on GitHub. Please add as much detail as possible. Including the 
version and a reproducer. The more the community knows the more it can help :-)

### Feature Requests

If you have a feature request or questions about the direction of the project please as via a 
GitHub issue.

### Large changes or improvements

We are excited to accept new contributors and larger changes. Please post a proposal 
before submitting a large change. This helps avoid double work and allows the community to arrive at a consensus
on the new feature or improvement.

## Code changes

### Development process

The development process doesn't contain many surprises. As most projects on github anyone can contribute by
forking the repo and posting a pull request. See 
[GitHub's documentation](https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/creating-a-pull-request-from-a-fork) 
for more information. Small changes don't require an issue. However, it is good practice to open up an issue for
larger changes.
The [good first issue](https://github.com/projectnessie/cel-java/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) label marks issues
that are particularly good for people new to the codebase.

### Style guide

Changes must adhere to the style guide and this will be verified by the continuous integration build.

* Java code style is [Google style](https://google.github.io/styleguide/javaguide.html).

Java code style is checked by [Spotless](https://github.com/diffplug/spotless)
with [google-java-format](https://github.com/google/google-java-format) during build.

#### Configuring the Code Formatter for Intellij IDEA and Eclipse

Follow the instructions for [Eclipse](https://github.com/google/google-java-format#eclipse) or
[IntelliJ](https://github.com/google/google-java-format#intellij-android-studio-and-other-jetbrains-ides),
note the required manual actions for IntelliJ.

#### Automatically fixing code style issues

Java and Scala code style issues can be fixed from the command line using
`./gradlew spotlessApply`.

### Submitting a pull request

Anyone can take part in the review process and once the community is happy and the build actions are passing a
Pull Request will be merged. Support must be unanimous for a change to be merged.

### Reporting security issues

Please see our [Security Policy](SECURITY.md)
