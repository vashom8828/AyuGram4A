/*
 * This is the source code of AyuGram for Android.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.proprietary;

import android.text.TextUtils;
import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.database.entities.DeletedMessage;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

public class AyuMessageUtils {

    public static void map(AyuSavePreferences prefs, EditedMessage revision) {
        mapBase(prefs, revision);
    }

    public static void map(AyuSavePreferences prefs, DeletedMessage deletedMessage) {
        mapBase(prefs, deletedMessage);
    }

    public static void mapBase(AyuSavePreferences prefs, AyuMessageBase entity) {
        if (prefs == null || entity == null) return;
        entity.userId = prefs.getUserId();
        entity.dialogId = prefs.getDialogId();
        entity.topicId = prefs.getTopicId();
        entity.messageId = prefs.getMessageId();
        entity.entityCreateDate = prefs.getRequestCatchTime();

        TLRPC.Message msg = prefs.getMessage();
        if (msg == null) return;

        entity.date = msg.date;
        entity.flags = msg.flags;
        entity.editDate = msg.edit_date;
        entity.groupedId = msg.grouped_id;
        entity.views = msg.views;
        entity.fromId = MessageObject.getFromChatId(msg);
        entity.peerId = msg.peer_id != null ? MessageObject.getPeerId(msg.peer_id) : 0;
        entity.text = msg.message;

        if (msg.entities != null && !msg.entities.isEmpty()) {
            try {
                SerializedData data = new SerializedData();
                data.writeInt32(0x1cb5c415);
                int count = msg.entities.size();
                data.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    msg.entities.get(a).serializeToStream(data);
                }
                entity.textEntities = data.toByteArray();
                data.cleanup();
            } catch (Exception ignored) {}
        }

        if (msg.fwd_from != null) {
            entity.fwdFlags = msg.fwd_from.flags;
            entity.fwdDate = msg.fwd_from.date;
            entity.fwdFromId = msg.fwd_from.from_id != null ? MessageObject.getPeerId(msg.fwd_from.from_id) : 0;
            entity.fwdName = msg.fwd_from.from_name;
            entity.fwdPostAuthor = msg.fwd_from.post_author;
        }

        if (msg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
            TLRPC.TL_messageReplyHeader header = (TLRPC.TL_messageReplyHeader) msg.reply_to;
            entity.replyFlags = header.flags;
            entity.replyMessageId = header.reply_to_msg_id;
            entity.replyTopId = header.reply_to_top_id;
            entity.replyForumTopic = header.forum_topic;
            if (header.reply_to_peer_id != null) {
                entity.replyPeerId = MessageObject.getPeerId(header.reply_to_peer_id);
            }
        }
    }

    public static void mapMedia(AyuSavePreferences prefs, EditedMessage revision, boolean copyMedia) {
        mapMediaBase(prefs, revision, copyMedia);
    }

    public static void mapMedia(AyuSavePreferences prefs, DeletedMessage deletedMessage, boolean copyMedia) {
        mapMediaBase(prefs, deletedMessage, copyMedia);
    }

    public static void mapMediaBase(AyuSavePreferences prefs, AyuMessageBase entity, boolean copyMedia) {
        if (prefs == null || entity == null) return;
        TLRPC.Message msg = prefs.getMessage();
        if (msg == null || msg.media == null) {
            entity.documentType = AyuConstants.DOCUMENT_TYPE_NONE;
            return;
        }

        if (msg.media instanceof TLRPC.TL_messageMediaPhoto && msg.media.photo != null) {
            entity.documentType = AyuConstants.DOCUMENT_TYPE_PHOTO;
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(msg.media.photo.sizes, 1280);
            if (size != null) {
                File path = FileLoader.getInstance(prefs.getAccountId()).getPathToAttach(size, true);
                if (path != null && path.exists()) {
                    entity.mediaPath = path.getAbsolutePath();
                }
            }
        } else if (msg.media instanceof TLRPC.TL_messageMediaDocument && msg.media.document != null) {
            TLRPC.Document doc = msg.media.document;
            entity.mimeType = doc.mime_type;
            if (MessageObject.isStickerDocument(doc)) {
                entity.documentType = AyuConstants.DOCUMENT_TYPE_STICKER;
            } else {
                entity.documentType = AyuConstants.DOCUMENT_TYPE_FILE;
            }

            try {
                SerializedData data = new SerializedData(doc.getObjectSize());
                doc.serializeToStream(data);
                entity.documentSerialized = data.toByteArray();
                data.cleanup();
            } catch (Exception ignored) {}

            File path = FileLoader.getInstance(prefs.getAccountId()).getPathToAttach(doc, true);
            if (path != null && path.exists()) {
                entity.mediaPath = path.getAbsolutePath();
            }
        }
    }

    public static void map(EditedMessage editedMessage, TLRPC.Message msg, int currentAccount) {
        mapMsgBase(editedMessage, msg, currentAccount);
    }

    public static void map(DeletedMessage deletedMessage, TLRPC.Message msg, int currentAccount) {
        mapMsgBase(deletedMessage, msg, currentAccount);
    }

    public static void mapMsgBase(AyuMessageBase entity, TLRPC.Message msg, int currentAccount) {
        if (entity == null || msg == null) return;
        msg.id = entity.messageId;
        msg.dialog_id = entity.dialogId;
        msg.date = entity.date;
        msg.flags = entity.flags;
        msg.edit_date = entity.editDate;
        msg.grouped_id = entity.groupedId;
        msg.views = entity.views;
        msg.message = entity.text != null ? entity.text : "";
        msg.ayuDeleted = true;
        msg.destroyTime = entity.entityCreateDate;

        if (entity.peerId != 0) {
            msg.peer_id = MessagesController.getInstance(currentAccount).getPeer(entity.peerId);
        }
        if (entity.fromId != 0) {
            msg.from_id = MessagesController.getInstance(currentAccount).getPeer(entity.fromId);
        }

        if (entity.textEntities != null && entity.textEntities.length > 0) {
            try {
                SerializedData data = new SerializedData(entity.textEntities);
                int magic = data.readInt32(false);
                if (magic == 0x1cb5c415) {
                    int count = data.readInt32(false);
                    msg.entities = new ArrayList<>();
                    for (int a = 0; a < count; a++) {
                        msg.entities.add(TLRPC.MessageEntity.TLdeserialize(data, data.readInt32(false), false));
                    }
                }
                data.cleanup();
            } catch (Exception ignored) {}
        }

        if (entity.fwdDate != 0 || entity.fwdFromId != 0 || !TextUtils.isEmpty(entity.fwdName)) {
            TLRPC.TL_messageFwdHeader fwd = new TLRPC.TL_messageFwdHeader();
            fwd.flags = entity.fwdFlags;
            fwd.date = entity.fwdDate;
            fwd.from_name = entity.fwdName;
            fwd.post_author = entity.fwdPostAuthor;
            if (entity.fwdFromId != 0) {
                fwd.from_id = MessagesController.getInstance(currentAccount).getPeer(entity.fwdFromId);
            }
            msg.fwd_from = fwd;
        }

        if (entity.replyMessageId != 0) {
            TLRPC.TL_messageReplyHeader replyHeader = new TLRPC.TL_messageReplyHeader();
            replyHeader.flags = entity.replyFlags;
            replyHeader.reply_to_msg_id = entity.replyMessageId;
            replyHeader.reply_to_top_id = entity.replyTopId;
            replyHeader.forum_topic = entity.replyForumTopic;
            if (entity.replyPeerId != 0) {
                replyHeader.reply_to_peer_id = MessagesController.getInstance(currentAccount).getPeer(entity.replyPeerId);
            }
            msg.reply_to = replyHeader;
        }
    }

    public static void mapMedia(EditedMessage editedMessage, TLRPC.Message msg) {
        mapMediaMsgBase(editedMessage, msg);
    }

    public static void mapMedia(DeletedMessage deletedMessage, TLRPC.Message msg) {
        mapMediaMsgBase(deletedMessage, msg);
    }

    public static void mapMediaMsgBase(AyuMessageBase entity, TLRPC.Message msg) {
        if (entity == null || msg == null) return;
        if (entity.documentType == AyuConstants.DOCUMENT_TYPE_PHOTO) {
            if (!TextUtils.isEmpty(entity.mediaPath) && new File(entity.mediaPath).exists()) {
                TLRPC.TL_messageMediaPhoto mediaPhoto = new TLRPC.TL_messageMediaPhoto();
                TLRPC.TL_photo photo = new TLRPC.TL_photo();
                photo.sizes = new ArrayList<>();
                TLRPC.TL_photoSize size = new TLRPC.TL_photoSize();
                size.type = "x";
                size.w = 800;
                size.h = 800;
                size.size = (int) new File(entity.mediaPath).length();
                photo.sizes.add(size);
                mediaPhoto.photo = photo;
                msg.media = mediaPhoto;
            }
        } else if (entity.documentType == AyuConstants.DOCUMENT_TYPE_STICKER || entity.documentType == AyuConstants.DOCUMENT_TYPE_FILE) {
            if (entity.documentSerialized != null && entity.documentSerialized.length > 0) {
                try {
                    SerializedData data = new SerializedData(entity.documentSerialized);
                    TLRPC.Document document = TLRPC.Document.TLdeserialize(data, data.readInt32(false), false);
                    data.cleanup();
                    if (document != null) {
                        TLRPC.TL_messageMediaDocument mediaDoc = new TLRPC.TL_messageMediaDocument();
                        mediaDoc.document = document;
                        msg.media = mediaDoc;
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
