# Analyzer example - Kotlin Coroutines version

To run the example:

```
./gradlew :analyzer-direct-kt:run
```

In order to run the application you need to place inside the `analyzer-commons` directory a `.env` file containing [your personal GitHub access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens), like:

```env
GH_TOKEN=....
```

or having set an environment variable named `GH_TOKEN`.

![Analyzer example](../docs/site/content/res/img/analyzer-e2e.png)

Have a look to [the documentation](https://tassiluca.github.io/direct-style-experiments/docs/03-channels/) for more details.
