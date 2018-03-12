package me.aboullaite;

import com.github.messenger4j.Messenger;
import com.github.messenger4j.common.WebviewHeightRatio;
import com.github.messenger4j.exception.MessengerApiException;
import com.github.messenger4j.exception.MessengerIOException;
import com.github.messenger4j.exception.MessengerVerificationException;
import com.github.messenger4j.send.*;
import com.github.messenger4j.send.message.TemplateMessage;
import com.github.messenger4j.send.message.TextMessage;
import com.github.messenger4j.send.message.quickreply.LocationQuickReply;
import com.github.messenger4j.send.message.quickreply.QuickReply;
import com.github.messenger4j.send.message.quickreply.TextQuickReply;
import com.github.messenger4j.send.message.template.ButtonTemplate;
import com.github.messenger4j.send.message.template.button.Button;
import com.github.messenger4j.send.message.template.button.PostbackButton;
import com.github.messenger4j.send.message.template.button.UrlButton;
import com.github.messenger4j.send.recipient.IdRecipient;
import com.github.messenger4j.send.recipient.Recipient;
import com.github.messenger4j.send.senderaction.SenderAction;
import com.github.messenger4j.webhook.event.QuickReplyMessageEvent;
import com.github.messenger4j.webhook.event.TextMessageEvent;
import me.aboullaite.domain.QuickReplyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/")
public class CallBackHandler {

    private static final Logger logger = LoggerFactory.getLogger(CallBackHandler.class);

    private final Messenger sendClient;

    @Autowired
    public CallBackHandler(final Messenger messenger) {

        this.sendClient = messenger;
        logger.debug("Initializing CallBackHandler");

//        sendClient.onReceiveEvents();

//        this.receiveClient = MessengerPlatform.newReceiveClientBuilder(appSecret, verifyToken)
//                .onTextMessageEvent(newTextMessageEventHandler())
//                .onQuickReplyMessageEvent(newQuickReplyMessageEventHandler())
//                .onPostbackEvent(newPostbackEventHandler())
//                .onAccountLinkingEvent(newAccountLinkingEventHandler())
//                .onOptInEvent(newOptInEventHandler())
//                .onEchoMessageEvent(newEchoMessageEventHandler())
//                .onMessageDeliveredEvent(newMessageDeliveredEventHandler())
//                .onMessageReadEvent(newMessageReadEventHandler())
//                .fallbackEventHandler(newFallbackEventHandler())
//                .build();
//        this.sendClient = sendClient;
    }

    /**
     * Webhook verification endpoint.
     *
     * The passed verification token (as query parameter) must match the configured verification token.
     * In case this is true, the passed challenge string must be returned by this endpoint.
     */
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<String> verifyWebhook(@RequestParam("hub.mode") final String mode,
                                                @RequestParam("hub.verify_token") final String verifyToken,
                                                @RequestParam("hub.challenge") final String challenge) {

        logger.debug("Received Webhook verification request - mode: {} | verifyToken: {} | challenge: {}", mode,
                verifyToken, challenge);
        try {
            this.sendClient.verifyWebhook(mode, verifyToken);
            return ResponseEntity.ok(challenge);
        } catch (MessengerVerificationException e) {
            logger.warn("Webhook verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * Callback endpoint responsible for processing the inbound messages and events.
     */
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<Void> handleCallback(@RequestBody final String payload,
                                               @RequestHeader("X-Hub-Signature") final String signature) {

        logger.debug("Received Messenger Platform callback - payload: {} | signature: {}", payload, signature);
        try {
            sendClient.onReceiveEvents(payload, Optional.ofNullable(signature), event -> {
                final String senderId = event.senderId();
                final Instant timestamp = event.timestamp();

                if (event.isTextMessageEvent()) {
                    final TextMessageEvent textMessageEvent = event.asTextMessageEvent();
                    final String messageId = textMessageEvent.messageId();
                    final String text = textMessageEvent.text();

                    logger.debug("Received text message from '{}' at '{}' with content: {} (mid: {})",
                            senderId, timestamp, text, messageId);

                    processTextMessage(messageId, text, senderId, event.timestamp());
                } else if (event.isQuickReplyMessageEvent()) {
                    QuickReplyMessageEvent quickReplyEvent = event.asQuickReplyMessageEvent();
                    quickReplyMessageHandler(quickReplyEvent.senderId(), quickReplyEvent.payload());
                }
            });

            logger.debug("Processed callback payload successfully");
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (MessengerVerificationException e) {
            logger.warn("Processing of callback payload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    private void processTextMessage(final String messageId,   final String messageText,   final String senderId,   final Instant timestamp) {
        logger.info("Received message '{}' with text '{}' from user '{}' at '{}'", messageId, messageText, senderId, timestamp);
        try {
            switch (messageText.toLowerCase()) {
                case "მადლობა":
                    sendTextMessage(senderId, "მადლობა დაინტერესებისთვის <3");
                    break;
                case "madloba":
                    sendTextMessage(senderId, "არაფერს #წერექართულად");
                    break;
                default:
                    sendAction(senderId, SenderAction.MARK_SEEN);
                    sendAction(senderId, SenderAction.TYPING_ON);
                    sendQuickReply(senderId);
                    sendButtonTemplate(senderId);
                    sendAction(senderId, SenderAction.TYPING_OFF);
            }
        } catch (Exception e) {
            handleSendException(e);
        }
    }

    private void sendButtonTemplate(String recipientId) throws  Exception {
        UrlButton buttonA = UrlButton.create("Show Website", new URL("http://example.com"));
        PostbackButton buttonB = PostbackButton.create("Start Chatting", "USER_DEFINED_PAYLOAD");
        UrlButton buttonC = UrlButton.create("Show Website", new URL("https://petersapparel.parseapp.com"),
                Optional.of(WebviewHeightRatio.FULL), Optional.of(true), Optional.of(new URL("https://petersfancyapparel.com/fallback")), Optional.empty());

        final List<Button> buttons = Arrays.asList(buttonA, buttonB, buttonC);
        final ButtonTemplate buttonTemplate = ButtonTemplate.create("What do you want to do next?", buttons);

        final TemplateMessage templateMessage = TemplateMessage.create(buttonTemplate);
        final MessagePayload payload = MessagePayload.create(recipientId, MessagingType.RESPONSE,
                templateMessage);

        sendClient.send(payload);
    }

    private void sendOptions(String recipientId) {

//        final List<Button> firstLink = Button.newListBuilder()
//                .addUrlButton("შოპი", "www.example.com").toList()
//                .build();
//
//        final GenericTemplate genericTemplate = GenericTemplate.newBuilder()
//                .addElements()
//                .addElement("რა ხირს?")
//                .buttons(firstLink)
//                .toList()
//                .addElement("მაღაზია სად გაქვთ?")
//                .subtitle("სად მოვიდე?")
//                .itemUrl("https://goo.gl/y1uFAL")
//                .buttons(firstLink)
//                .toList()
//                .done()
//                .build();
//
//        try {
//            this.sendClient.sendTemplate(recipientId, genericTemplate);
//        } catch (MessengerApiException e) {
//            e.printStackTrace();
//        } catch (MessengerIOException e) {
//            e.printStackTrace();
//        }
//        try {
//            final UrlButton buttonA = UrlButton.create("Show Website", new URL("https://petersapparel.parseapp.com"));
//            final PostbackButton buttonB = PostbackButton.create("Start Chatting", "USER_DEFINED_PAYLOAD");
//            final UrlButton buttonC = UrlButton.create("Show Website", new URL("https://petersapparel.parseapp.com"),
//                    WebviewHeightRatio.FULL, true, new URL("https://petersfancyapparel.com/fallback"), null);
//
//            final List<Button> buttons = Arrays.asList(buttonA, buttonB, buttonC);
//            final ButtonTemplate buttonTemplate = ButtonTemplate.create("What do you want to do next?", buttons);
//
//            final TemplateMessage templateMessage = TemplateMessage.create(buttonTemplate);
//            final MessagePayload payload = MessagePayload.create(recipientId, MessagingType.RESPONSE,
//                    templateMessage);
//
//            sendClient.send(payload);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

//    private void sendGifMessage(String recipientId, String gif) throws MessengerApiException, MessengerIOException {
//        this.sendClient.sendImageAttachment(recipientId, gif);
//    }



    private void sendQuickReply(String recipientId) throws Exception {
        final IdRecipient recipient = IdRecipient.create(recipientId);

        final String text = "გთხოვთ აირჩიოთ თქვენთვის სასურველი კითხვა";

        final TextQuickReply quickReplyA = TextQuickReply.create("მაღაზია",
                QuickReplyType.SHOP.toString(), Optional.of(new URL("http://example.com/img/red.png")));
        final LocationQuickReply quickReplyB = LocationQuickReply.create();
        final TextQuickReply quickReplyC = TextQuickReply.create("რა ხირს?", QuickReplyType.PRICE.toString());

        final List<QuickReply> quickReplies = Arrays.asList(quickReplyA, quickReplyB, quickReplyC);

        final TextMessage message = TextMessage.create(text, Optional.of(quickReplies), Optional.empty());
        final MessagePayload payload = MessagePayload.create(recipient, MessagingType.RESPONSE, message);

        sendClient.send(payload);


//        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
//                .addTextQuickReply("რა ხირს?", PRICE).toList()
//                .addTextQuickReply("როგორ ვიყიდო?", LOCATION).toList()
//                .addTextQuickReply("სად გაქვთ მაღაზია?", SHOP).toList()
//                .build();

//        this.sendClient.sendTextMessage(recipientId, "გთხოვთ აირჩიოთ თქვენთვის სასურველი კითხვა", quickReplies);
    }

    private void sendAction(String recipientId, SenderAction action) throws MessengerApiException, MessengerIOException {
        sendClient.send(SenderActionPayload.create(recipientId, action));
    }

    private void quickReplyMessageHandler(String senderId, String quickReplyPayload) {
        QuickReplyType replyType = QuickReplyType.valueOf(quickReplyPayload);
        if(replyType == QuickReplyType.PRICE) {
            sendTextMessage(senderId, "30 გელა");
        } else if (replyType == QuickReplyType.SHOP) {
            sendTextMessage(senderId, "არ გვაქვს მაღაზია ბლა ბლა ბლა");
        } else if (replyType == QuickReplyType.LOCATION) {
            sendTextMessage(senderId, "ჭავჭავაძეზე მოდი ბრატ");
        }
        try {
            sendQuickReply(senderId);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

//    private PostbackEventHandler newPostbackEventHandler() {
//        return event -> {
//            logger.debug("Received PostbackEvent: {}", event);
//
//            final String senderId = event.getSender().getId();
//            final String recipientId = event.getRecipient().getId();
//            final String payload = event.getPayload();
//            final Date timestamp = event.getTimestamp();
//
//            logger.info("Received postback for user '{}' and page '{}' with payload '{}' at '{}'",
//                    senderId, recipientId, payload, timestamp);
//
//            sendTextMessage(senderId, "Postback called");
//        };
//    }

//    private AccountLinkingEventHandler newAccountLinkingEventHandler() {
//        return event -> {
//            logger.debug("Received AccountLinkingEvent: {}", event);
//
//            final String senderId = event.getSender().getId();
//            final AccountLinkingEvent.AccountLinkingStatus accountLinkingStatus = event.getStatus();
//            final String authorizationCode = event.getAuthorizationCode();
//
//            logger.info("Received account linking event for user '{}' with status '{}' and auth code '{}'",
//                    senderId, accountLinkingStatus, authorizationCode);
//        };
//    }

//    private OptInEventHandler newOptInEventHandler() {
//        return event -> {
//            logger.debug("Received OptInEvent: {}", event);
//
//            final String senderId = event.getSender().getId();
//            final String recipientId = event.getRecipient().getId();
//            final String passThroughParam = event.getRef();
//            final Date timestamp = event.getTimestamp();
//
//            logger.info("Received authentication for user '{}' and page '{}' with pass through param '{}' at '{}'",
//                    senderId, recipientId, passThroughParam, timestamp);
//
//            sendTextMessage(senderId, "Authentication successful");
//        };
//    }

//    private EchoMessageEventHandler newEchoMessageEventHandler() {
//        return event -> {
//            logger.debug("Received EchoMessageEvent: {}", event);
//
//            final String messageId = event.getMid();
//            final String recipientId = event.getRecipient().getId();
//            final String senderId = event.getSender().getId();
//            final Date timestamp = event.getTimestamp();
//
//            logger.info("Received echo for message '{}' that has been sent to recipient '{}' by sender '{}' at '{}'",
//                    messageId, recipientId, senderId, timestamp);
//        };
//    }

//    private MessageDeliveredEventHandler newMessageDeliveredEventHandler() {
//        return event -> {
//            logger.debug("Received MessageDeliveredEvent: {}", event);
//
//            final List<String> messageIds = event.getMids();
//            final Date watermark = event.getWatermark();
//            final String senderId = event.getSender().getId();
//
//            if (messageIds != null) {
//                messageIds.forEach(messageId -> {
//                    logger.info("Received delivery confirmation for message '{}'", messageId);
//                });
//            }
//
//            logger.info("All messages before '{}' were delivered to user '{}'", watermark, senderId);
//        };
//    }

//    private MessageReadEventHandler newMessageReadEventHandler() {
//        return event -> {
//            logger.debug("Received MessageReadEvent: {}", event);
//
//            final Date watermark = event.getWatermark();
//            final String senderId = event.getSender().getId();
//
//            logger.info("All messages before '{}' were read by user '{}'", watermark, senderId);
//        };
//    }

    /**
     * This handler is called when either the message is unsupported or when the event handler for the actual event type
     * is not registered. In this showcase all event handlers are registered. Hence only in case of an
     * unsupported message the fallback event handler is called.
     */
//    private void newFallbackEventHandler() {
//        return event -> {
//            logger.debug("Received FallbackEvent: {}", event);
//
//            final String senderId = event.getSender().getId();
//            logger.info("Received unsupported message from user '{}'", senderId);
//        };
//    }

    private void sendTextMessage(String recipientId, String text) {
        try {
            final MessagePayload payload = MessagePayload.create(recipientId,
                    MessagingType.RESPONSE, TextMessage.create(text));

            sendClient.send(payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSendException(Exception e) {
        logger.error("Message could not be sent. An unexpected error occurred.", e);
    }

    private void handleIOException(Exception e) {
        logger.error("Could not open Spring.io page. An unexpected error occurred.", e);
    }
}
