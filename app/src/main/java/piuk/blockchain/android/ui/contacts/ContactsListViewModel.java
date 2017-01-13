package piuk.blockchain.android.ui.contacts;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.util.Log;

import info.blockchain.wallet.contacts.data.Contact;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import piuk.blockchain.android.R;
import piuk.blockchain.android.data.datamanagers.ContactsDataManager;
import piuk.blockchain.android.data.datamanagers.QrCodeDataManager;
import piuk.blockchain.android.data.notifications.FcmCallbackService;
import piuk.blockchain.android.injection.Injector;
import piuk.blockchain.android.ui.base.BaseViewModel;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.util.PrefsUtil;

import static piuk.blockchain.android.ui.contacts.ContactsListActivity.EXTRA_METADATA_URI;

@SuppressWarnings("WeakerAccess")
public class ContactsListViewModel extends BaseViewModel {

    private static final String TAG = ContactsListViewModel.class.getSimpleName();

    private DataListener dataListener;
    @Inject QrCodeDataManager qrCodeDataManager;
    @Inject ContactsDataManager contactsDataManager;
    @Inject PrefsUtil prefsUtil;

    interface DataListener {

        Intent getPageIntent();

        void onContactsLoaded(@NonNull List<ContactsListItem> contacts);

        void setUiState(@ContactsListActivity.UiState int uiState);

        void showToast(@StringRes int message, @ToastCustom.ToastType String toastType);

        void showProgressDialog();

        void dismissProgressDialog();

    }

    ContactsListViewModel(DataListener dataListener) {
        Injector.getInstance().getDataManagerComponent().inject(this);
        this.dataListener = dataListener;
    }

    @Override
    public void onViewReady() {
        // Subscribe to notification events
        subscribeToNotifications();

        // TODO: 13/01/2017 Separate getting actual contacts and pending contacts
        // When pending contacts are present, start polling for readInvitationSent(contact)
        // I know it's horrible ¯\_(ツ)_/¯

        dataListener.setUiState(ContactsListActivity.LOADING);
        compositeDisposable.add(
                contactsDataManager.fetchContacts()
                        .andThen(getAllContacts())
                        .toList()
                        .subscribe(
                                this::handleContactListUpdate,
                                throwable -> dataListener.setUiState(ContactsListActivity.FAILURE)));

        Intent intent = dataListener.getPageIntent();
        if (intent != null && intent.hasExtra(EXTRA_METADATA_URI)) {
            String data = intent.getStringExtra(EXTRA_METADATA_URI);
            handleLink(data);
        }
    }

    private Observable<Contact> getAllContacts() {
        return Observable.merge(contactsDataManager.getContactList(), contactsDataManager.getPendingContactList());
    }

    /**
     * Get the latest version stored on disk
     */
    void requestUpdatedList() {
        compositeDisposable.add(
                getAllContacts()
                        .toList()
                        .subscribe(
                                this::handleContactListUpdate,
                                throwable -> dataListener.setUiState(ContactsListActivity.FAILURE)));
    }

    private void subscribeToNotifications() {
        FcmCallbackService.getNotificationSubject().subscribe(
                notificationPayload -> {
                    Log.d(TAG, "subscribeToNotifications: ");
                    // TODO: 02/12/2016 Filter specific events that are relevant to this page
                }, throwable -> {
                    Log.e(TAG, "subscribeToNotifications: ", throwable);
                });
    }

    private void handleContactListUpdate(List<Contact> contacts) {
        ArrayList<ContactsListItem> list = new ArrayList<>();

        for (Contact contact : contacts) {
            list.add(new ContactsListItem(contact.getId(), contact.getName(), ContactsListItem.Status.PENDING));
        }

        if (!list.isEmpty()) {
            dataListener.setUiState(ContactsListActivity.CONTENT);
            dataListener.onContactsLoaded(list);
        } else {
            dataListener.onContactsLoaded(new ArrayList<>());
            dataListener.setUiState(ContactsListActivity.EMPTY);
        }
    }

    private void handleLink(String data) {
        dataListener.showProgressDialog();

        compositeDisposable.add(
                contactsDataManager.acceptInvitation(data)
                        .flatMapCompletable(contact -> contactsDataManager.addContact(contact))
                        .andThen(contactsDataManager.saveContacts())
                        .andThen(contactsDataManager.getContactList())
                        .doAfterTerminate(() -> dataListener.dismissProgressDialog())
                        .toList()
                        .subscribe(
                                contacts -> {
                                    handleContactListUpdate(contacts);
                                    dataListener.showToast(R.string.contacts_add_contact_success, ToastCustom.TYPE_GENERAL);
                                }, throwable -> dataListener.showToast(R.string.contacts_add_contact_failed, ToastCustom.TYPE_ERROR)));

    }

//
//    @Thunk
//    void addUser(MetaDataUri metaDataUri) {
//        String name = metaDataUri.getFrom();
//        String inviteId = metaDataUri.getInviteId();
//
//        dataListener.showProgressDialog();
//
//        compositeDisposable.add(
//                contactManager.acceptInvitation(inviteId)
//                        .doAfterTerminate(() -> dataListener.dismissProgressDialog())
//                        .flatMap(invitation -> contactManager.getContactList())
//                        .subscribe(trusted -> {
//                            handleContactListUpdate(trusted);
//                            dataListener.showToast(R.string.contacts_add_contact_success, ToastCustom.TYPE_OK);
//                        }, throwable -> dataListener.showToast(R.string.contacts_add_contact_failed, ToastCustom.TYPE_ERROR)));
//    }
}
