# telegram-bot

Basic bot for Telegram based on the Java code by [ex3ndr](https://github.com/ex3ndr/telegram-bot).

This bot is not using the Bot API. Instead it uses the Telegram API directly using an ordinary Telegram account. Before using the code in production you should [obtain your own api_id and api_hash](https://core.telegram.org/api/obtaining_api_id) and replace ex3ndr's values that are currently in the code.

To use this code from Eclipse you may follow these steps:

1. Optionally fork this project. Please note that this will only fork the main project and not the submodules in the library project.
2. Clone the project or your fork of it
3. Run "gradle eclipse" in the app folder as well as each of the 3 library projects
4. Import the 4 projects into Eclipse
5. Replace the api_id and api_hash with your own values (see above)

If you want to edit code in one of the submodules you probably want to fork it first, then look at one of the tutorials such as http://patrickward.com/2013/01/09/using-git-submodules/.
