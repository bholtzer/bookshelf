package com.bihstudio.madafim.domain.analytics

interface AnalyticsLogger {
    fun setUserId(userId: String?)
    fun trackScreen(screenName: String)
    fun track(eventName: String, params: Map<String, Any?> = emptyMap())
}

object AnalyticsEvent {
    const val APP_OPENED = "app_opened"
    const val OPENING_FINISHED = "opening_finished"
    const val AUTH_EMAIL_STARTED = "auth_email_started"
    const val AUTH_EMAIL_RESULT = "auth_email_result"
    const val AUTH_GOOGLE_STARTED = "auth_google_started"
    const val AUTH_GOOGLE_RESULT = "auth_google_result"
    const val AUTH_REGISTER_STARTED = "auth_register_started"
    const val AUTH_REGISTER_RESULT = "auth_register_result"
    const val AUTH_MODE_CHANGED = "auth_mode_changed"
    const val BOOK_CREATE_STARTED = "book_create_started"
    const val BOOK_CREATE_RESULT = "book_create_result"
    const val BOOK_OPENED = "book_opened"
    const val BOOK_EDIT_OPENED = "book_edit_opened"
    const val BOOK_UPDATED = "book_updated"
    const val BOOK_DELETED = "book_deleted"
    const val PAGES_ADD_STARTED = "pages_add_started"
    const val PAGES_ADD_RESULT = "pages_add_result"
    const val PAGE_DELETE_RESULT = "page_delete_result"
    const val PAGE_REMOVE_RECOMMEND_RESULT = "page_remove_recommend_result"
    const val COVER_PHOTO_RESULT = "cover_photo_result"
    const val COVER_STYLE_RESULT = "cover_style_result"
    const val EDITOR_SHARE_RESULT = "editor_share_result"
    const val PRINT_STARTED = "print_started"
    const val READER_PAGES_LOADED = "reader_pages_loaded"
    const val SHARE_RECEIVED = "share_received"
    const val SHARE_SHEET_DISMISSED = "share_sheet_dismissed"
    const val SHARE_ADD_TO_BOOK_RESULT = "share_add_to_book_result"
    const val SHARE_CREATE_BOOK_RESULT = "share_create_book_result"
    const val SHELF_LOADED = "shelf_loaded"
    const val PAYWALL_VIEWED = "paywall_viewed"
    const val PURCHASE_STARTED = "purchase_started"
}

object AnalyticsParam {
    const val AUTH_METHOD = "auth_method"
    const val BOOK_ID = "book_id"
    const val BOOK_COUNT = "book_count"
    const val CAN_EDIT = "can_edit"
    const val FILE_COUNT = "file_count"
    const val HAS_DESCRIPTION = "has_description"
    const val MIME_TYPE = "mime_type"
    const val PAGE_COUNT = "page_count"
    const val RESULT = "result"
    const val SCREEN_NAME = "screen_name"
    const val SOURCE = "source"
    const val STYLE = "style"
}
