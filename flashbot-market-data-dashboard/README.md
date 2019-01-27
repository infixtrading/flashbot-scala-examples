This page shows how to configure a market data database, fill it up with live and historical data using Flashbot,
and how to view that data in two ways: programmatically with `FlashbotClient` and interactively with Grafana.

All code snippets are in Scala, but are easily translated to Java. The source code for the example
in this tutorial is published for both Java and Scala.

**Contents**
1. [Setup the database](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#1-collect-market-data)
2. [Collect market data](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#2-run-backtests-using-flashbotclient)
3. [Poll market data with FlashbotClient](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#3-run-backtests-using-the-dashboard)
4. [Explore market data with the dashboard](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#3-run-backtests-using-the-dashboard)

*The source code for this tutorial can be found at:*
* Java: [flashbot-market-data-dashboard](https://github.com/infixtrading/flashbot-java-examples/tree/master/flashbot-market-data-dashboard)
* Scala: [flashbot-market-data-dashboard](https://github.com/infixtrading/flashbot-scala-examples/tree/master/flashbot-market-data-dashboard)

****