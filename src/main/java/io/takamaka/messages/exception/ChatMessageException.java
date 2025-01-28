/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ChatMessageException extends MessageException {

    private static final long serialVersionUID = -5349719876521084121L;

    public ChatMessageException() {
        super();
    }

    public ChatMessageException(String msg) {
        super(msg);
    }

    public ChatMessageException(Throwable er) {
        super(er);
    }

    public ChatMessageException(String msg, Throwable er) {
        super(msg, er);
    }
}
