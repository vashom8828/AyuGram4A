/*
 * This is the source code of AyuGram for Android.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.proprietary;

import android.util.Pair;
import android.util.SparseArray;
import com.radolyn.ayugram.AyuConfig;
import com.radolyn.ayugram.database.AyuDatabase;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

public class AyuHistoryHook {

    public static Pair<Integer, Integer> getMinAndMaxIds(ArrayList<MessageObject> messArr) {
        if (messArr == null || messArr.isEmpty()) {
            return new Pair<>(0, 0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < messArr.size(); i++) {
            MessageObject obj = messArr.get(i);
            if (obj != null) {
                int id = obj.getId();
                if (id < min) min = id;
                if (id > max) max = id;
            }
        }
        if (min == Integer.MAX_VALUE) {
            min = 0;
            max = 0;
        }
        return new Pair<>(min, max);
    }

    public static void doHook(int currentAccount, ArrayList<MessageObject> messArr, SparseArray<MessageObject>[] messagesDict, int startId, int endId, long dialogId, int limit, int topicId, boolean isSecretChat) {
        if (!AyuConfig.saveDeletedMessageFor(currentAccount, dialogId)) {
            return;
        }
        try {
            long userId = UserConfig.getInstance(currentAccount).getClientUserId();
            var dao = AyuDatabase.getInstance(currentAccount).deletedMessageDao();
            List<DeletedMessageFull> list = dao.getMessages(userId, dialogId, topicId, startId, endId, limit);
            if (list == null || list.isEmpty()) {
                return;
            }
            for (DeletedMessageFull item : list) {
                if (item == null || item.message == null) continue;
                int msgId = item.message.messageId;
                if (messagesDict != null && messagesDict.length > 0 && messagesDict[0] != null && messagesDict[0].indexOfKey(msgId) >= 0) {
                    continue;
                }

                TLRPC.TL_message msg = new TLRPC.TL_message();
                AyuMessageUtils.map(item.message, msg, currentAccount);
                AyuMessageUtils.mapMedia(item.message, msg);
                msg.date = item.message.date;

                MessageObject messageObject = new MessageObject(currentAccount, msg, false, false);
                if (messagesDict != null && messagesDict.length > 0 && messagesDict[0] != null) {
                    messagesDict[0].put(msgId, messageObject);
                }

                int index = 0;
                while (index < messArr.size() && messArr.get(index).getId() > msgId) {
                    index++;
                }
                messArr.add(index, messageObject);
            }
        } catch (Exception e) {
            FileLog.e("AyuHistoryHook", e);
        }
    }
}
