/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ChatMessageSerializationException extends ChatMessageException {

    private static final long serialVersionUID = 7121306851089874421L;

    public ChatMessageSerializationException() {
        super();
    }

    public ChatMessageSerializationException(String msg) {
        super(msg);
    }

    public ChatMessageSerializationException(Throwable er) {
        super(er);
    }

    public ChatMessageSerializationException(String msg, Throwable er) {
        super(msg, er);
    }
}
