package org.telegram.bot;

import static org.telegram.bot.BuildVars.API_HASH;
import static org.telegram.bot.BuildVars.API_ID;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.telegram.api.TLAbsInputFile;
import org.telegram.api.TLAbsInputPeer;
import org.telegram.api.TLAbsInputUser;
import org.telegram.api.TLAbsUpdates;
import org.telegram.api.TLConfig;
import org.telegram.api.TLInputFile;
import org.telegram.api.TLInputFileBig;
import org.telegram.api.TLInputMediaUploadedPhoto;
import org.telegram.api.TLInputPeerChat;
import org.telegram.api.TLInputPeerContact;
import org.telegram.api.TLInputUserContact;
import org.telegram.api.TLInputUserSelf;
import org.telegram.api.TLUpdateShortChatMessage;
import org.telegram.api.TLUpdateShortMessage;
import org.telegram.api.auth.TLAbsSentCode;
import org.telegram.api.auth.TLAuthorization;
import org.telegram.api.engine.ApiCallback;
import org.telegram.api.engine.AppInfo;
import org.telegram.api.engine.LoggerInterface;
import org.telegram.api.engine.RpcCallback;
import org.telegram.api.engine.RpcException;
import org.telegram.api.engine.TelegramApi;
import org.telegram.api.engine.file.Uploader;
import org.telegram.api.messages.TLAbsSentMessage;
import org.telegram.api.messages.TLAbsStatedMessage;
import org.telegram.api.requests.TLRequestAccountUpdateStatus;
import org.telegram.api.requests.TLRequestAuthSendCode;
import org.telegram.api.requests.TLRequestAuthSignIn;
import org.telegram.api.requests.TLRequestHelpGetConfig;
import org.telegram.api.requests.TLRequestMessagesCreateChat;
import org.telegram.api.requests.TLRequestMessagesDeleteChatUser;
import org.telegram.api.requests.TLRequestMessagesSendMedia;
import org.telegram.api.requests.TLRequestMessagesSendMessage;
import org.telegram.api.requests.TLRequestUpdatesGetState;
import org.telegram.api.updates.TLState;
import org.telegram.bot.engine.MemoryApiState;
import org.telegram.mtproto.log.LogInterface;
import org.telegram.mtproto.log.Logger;
import org.telegram.tl.TLVector;

/**
 * Created by ex3ndr on 13.01.14.
 */
public class Application {

	private static final String COMMAND_PREFIX = "/";

	private static final int PRIVATE = 0;

    private static MemoryApiState apiState;
    private static TelegramApi api;
    private static Random rnd = new Random();
    private static long lastOnline = System.currentTimeMillis();
    private static Executor mediaSender = Executors.newSingleThreadExecutor();

    public static void main(String[] args) throws IOException {
		setupLogging();
        createApi();
        login();
        workLoop();
    }

	private static void sendMedia(int uid, int chatId, String fileName) {
		TLAbsInputPeer inputPeer = chatId == PRIVATE ? new TLInputPeerContact(uid) : new TLInputPeerChat(chatId);

        int task = api.getUploader().requestTask(fileName, null);
        api.getUploader().waitForTask(task);
        int resultState = api.getUploader().getTaskState(task);
        Uploader.UploadResult result = api.getUploader().getUploadResult(task);
        TLAbsInputFile inputFile;
        if (result.isUsedBigFile()) {
            inputFile = new TLInputFileBig(result.getFileId(), result.getPartsCount(), "file.jpg");
        } else {
            inputFile = new TLInputFile(result.getFileId(), result.getPartsCount(), "file.jpg", result.getHash());
        }
        try {
            TLAbsStatedMessage res = api.doRpcCall(new TLRequestMessagesSendMedia(inputPeer, new TLInputMediaUploadedPhoto(inputFile), rnd.nextInt()), 30000);
            res.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	private static void sendMessage(int uid, int chatId, String message) {
		if (chatId == PRIVATE) {
			sendMessageUser(uid, message);
        } else {
			sendMessageChat(chatId, message);
        }
    }

    private static void sendMessageChat(int chatId, String message) {
        api.doRpcCall(new TLRequestMessagesSendMessage(new TLInputPeerChat(chatId), message, rnd.nextInt()),
                new RpcCallback<TLAbsSentMessage>() {
                    @Override
                    public void onResult(TLAbsSentMessage result) {

                    }

                    @Override
                    public void onError(int errorCode, String message) {
                    }
                });
    }

    private static void sendMessageUser(int uid, String message) {
        api.doRpcCall(new TLRequestMessagesSendMessage(new TLInputPeerContact(uid), message, rnd.nextInt()),
                new RpcCallback<TLAbsSentMessage>() {
                    @Override
                    public void onResult(TLAbsSentMessage result) {

                    }

                    @Override
                    public void onError(int errorCode, String message) {

                    }
                });
    }

	private static void createGroup(final int uid, final String title) {
        TLVector<TLAbsInputUser> users = new TLVector<>();
        users.add(new TLInputUserSelf());
        users.add(new TLInputUserContact(uid));
		api.doRpcCall(new TLRequestMessagesCreateChat(users, title),
                new RpcCallback<TLAbsStatedMessage>() {
                    @Override
                    public void onResult(TLAbsStatedMessage result) {
                    	System.out.println("Group created: " + title);
                        sendMessageUser(uid, "Group created: '" + title + "'");
                    }

                    @Override
                    public void onError(int errorCode, String message) {
                    	System.out.println("Error creating group: " + title + ", ErrorCode: " + 
                    			errorCode + ", Message: " + message);
                        sendMessageUser(uid, "Error: " + message);
                    }
                });
    }
    
	private static void kickUser(final int chatId, final int uid) {
		TLAbsInputUser user = new TLInputUserContact(uid);
		api.doRpcCall(new TLRequestMessagesDeleteChatUser(chatId, user), new RpcCallback<TLAbsStatedMessage>() {
			@Override
			public void onResult(TLAbsStatedMessage result) {
				System.out.println("Kicked user " + uid + " from chat " + chatId);
			}

			@Override
			public void onError(int errorCode, String message) {
				System.out.println("Error kicking " + uid + " from chat " + chatId + ", ErrorCode: " + errorCode
						+ ", Message: " + message);
				sendMessageChat(chatId, "Error: " + message);
			}
		});
	}

	private static void onIncomingMessage(int uid, int chatId, String message) {
		System.out.println("Incoming message from user #" + uid + " in chat #" + chatId + ": " + message);
		if (message.startsWith(COMMAND_PREFIX)) {
			processCommand(message.trim().substring(1), uid, chatId);
		} else {
			processCommand("help", uid, chatId);
        }
    }

	private static void processCommand(String message, final int uid, final int chatId) {
        String[] args = message.split(" ");
        if (args.length == 0) {
			sendMessage(uid, chatId, "Unknown command");
        }
        String command = args[0].trim().toLowerCase();
		String argument = message.substring(command.length()).trim();
		if (command.equals("ping")) {
			sendMessage(uid, chatId, "pong ");
        } else if (command.equals("help")) {
			sendMessage(uid, chatId, "Bot commands:\n" +
            		"/help - this help text\n" +
                    "/ping - pong\n" +
            		"/create-group [name]\n" +
            		"/kick [user id]\n" +
                    "/img - sending sample image\n");
        } else if (command.equals("create-group")) {
			if (argument.length() > 0) {
				createGroup(uid, argument);
			} else {
				createGroup(uid, "New Group");
			}
		} else if (command.equals("kick")) {
			if (chatId == PRIVATE) {
				sendMessageUser(uid, "You can only kick in groups");
			} else if (argument.length() > 0) {
				try {
					int victim = Integer.parseInt(args[1]);
					kickUser(chatId, victim);
				} catch (NumberFormatException e) {
					sendMessageChat(chatId, "'" + args[1] + "' is not a valid user id. Should be numeric.");
				}
			} else {
				sendMessageChat(chatId, "Missing user id\n/kick [user id]");
			}
        } else if (command.equals("img")) {
            mediaSender.execute(new Runnable() {
                @Override
                public void run() {
					sendMedia(uid, chatId, "demo.jpg");
                }
            });
        } else {
			sendMessage(uid, chatId, "Unknown command '" + args[0] + "'");
        }
    }

    private static void workLoop() {
        while (true) {
            try {
                if (System.currentTimeMillis() - lastOnline > 60 * 1000) {
                    api.doRpcCallWeak(new TLRequestAccountUpdateStatus(false));
                    lastOnline = System.currentTimeMillis();
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void setupLogging() {
        Logger.registerInterface(new LogInterface() {
            @Override
            public void w(String tag, String message) {
				// System.out.println(tag + ": " + message);
            }

            @Override
            public void d(String tag, String message) {

            }

            @Override
            public void e(String tag, Throwable t) {
				// t.printStackTrace();
            }
        });
        org.telegram.api.engine.Logger.registerInterface(new LoggerInterface() {
            @Override
            public void w(String tag, String message) {
				// System.out.println(tag + ": " + message);
            }

            @Override
            public void d(String tag, String message) {

            }

            @Override
            public void e(String tag, Throwable t) {
				// t.printStackTrace();
            }
        });
    }

    private static void createApi() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Use test DC? (write test for test servers): ");
        String res = reader.readLine();
        boolean useTest = res.equals("test");
        if (!useTest) {
            System.out.println("Using production servers");
        } else {
            System.out.println("Using test servers");
        }
        apiState = new MemoryApiState(useTest);
        api = new TelegramApi(apiState, new AppInfo(API_ID, "console", "???", "???", "en"), new ApiCallback() {

            @Override
            public void onAuthCancelled(TelegramApi api) {

            }

            @Override
            public void onUpdatesInvalidated(TelegramApi api) {

            }

            @Override
            public void onUpdate(TLAbsUpdates updates) {
                if (updates instanceof TLUpdateShortMessage) {
					TLUpdateShortMessage message = (TLUpdateShortMessage) updates;
					onIncomingMessage(message.getFromId(), 0, message.getMessage());
                } else if (updates instanceof TLUpdateShortChatMessage) {
					TLUpdateShortChatMessage message = (TLUpdateShortChatMessage) updates;
					onIncomingMessage(message.getFromId(), message.getChatId(), message.getMessage());
                }
            }
        });
    }

    private static void login() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Loading fresh DC list...");
        TLConfig config = api.doRpcCallNonAuth(new TLRequestHelpGetConfig());
        apiState.updateSettings(config);
        System.out.println("completed.");
        System.out.print("Phone number for bot:");
        String phone = reader.readLine();
        System.out.print("Sending sms to phone " + phone + "...");
		TLAbsSentCode sentCode;
        try {
            sentCode = api.doRpcCallNonAuth(new TLRequestAuthSendCode(phone, 0, API_ID, API_HASH, "en"));
        } catch (RpcException e) {
            if (e.getErrorCode() == 303) {
                int destDC;
                if (e.getErrorTag().startsWith("NETWORK_MIGRATE_")) {
                    destDC = Integer.parseInt(e.getErrorTag().substring("NETWORK_MIGRATE_".length()));
                } else if (e.getErrorTag().startsWith("PHONE_MIGRATE_")) {
                    destDC = Integer.parseInt(e.getErrorTag().substring("PHONE_MIGRATE_".length()));
                } else if (e.getErrorTag().startsWith("USER_MIGRATE_")) {
                    destDC = Integer.parseInt(e.getErrorTag().substring("USER_MIGRATE_".length()));
                } else {
                    throw e;
                }
                api.switchToDc(destDC);
                sentCode = api.doRpcCallNonAuth(new TLRequestAuthSendCode(phone, 0, API_ID, API_HASH, "en"));
            } else {
                throw e;
            }
        }
        System.out.println("sent.");
        System.out.print("Activation code:");
        String code = reader.readLine();
        TLAuthorization auth = api.doRpcCallNonAuth(new TLRequestAuthSignIn(phone, sentCode.getPhoneCodeHash(), code));
        apiState.setAuthenticated(apiState.getPrimaryDc(), true);
        System.out.println("Activation complete.");
        System.out.print("Loading initial state...");
        TLState state = api.doRpcCall(new TLRequestUpdatesGetState());
        System.out.println("loaded.");
    }
}
