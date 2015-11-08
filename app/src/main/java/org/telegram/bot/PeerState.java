package org.telegram.bot;

/**
 * Created by ex3ndr on 13.01.14.
 */
public class PeerState {
    private int id;
    private boolean isUser;

    public PeerState(int id, boolean isUser) {
        this.id = id;
        this.isUser = isUser;
    }

    public int getId() {
        return id;
    }

    public boolean isUser() {
        return isUser;
    }
}
