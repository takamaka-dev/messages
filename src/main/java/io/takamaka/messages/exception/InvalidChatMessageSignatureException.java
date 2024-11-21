/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class InvalidChatMessageSignatureException extends ChatMessageException {

    private static final long serialVersionUID = 1L;

    public InvalidChatMessageSignatureException() {
        super();
    }

    public InvalidChatMessageSignatureException(String msg) {
        super(msg);
    }

    public InvalidChatMessageSignatureException(Throwable er) {
        super(er);
    }

    public InvalidChatMessageSignatureException(String msg, Throwable er) {
        super(msg, er);
    }
}
