/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class UnsupportedChatMessageTypeException extends ChatMessageException {

    private static final long serialVersionUID = 1L;

    public UnsupportedChatMessageTypeException() {
        super();
    }

    public UnsupportedChatMessageTypeException(String msg) {
        super(msg);
    }

    public UnsupportedChatMessageTypeException(Throwable er) {
        super(er);
    }

    public UnsupportedChatMessageTypeException(String msg, Throwable er) {
        super(msg, er);
    }
}
