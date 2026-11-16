package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.model.UiState
import com.example.ui.viewmodel.MainViewModel

const val ROUTE_HOME = "home"
const val ROUTE_CHAT_DETAIL = "chat_detail/{chatId}"
const val ROUTE_SETTINGS = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseTalkApp(
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val localPrefs by viewModel.localPrefs.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val contacts by viewModel.contactsState.collectAsStateWithLifecycle()
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val devices by viewModel.devicesState.collectAsStateWithLifecycle()
    val backups by viewModel.backupsState.collectAsStateWithLifecycle()
    val statuses by viewModel.statusesState.collectAsStateWithLifecycle()
    val channels by viewModel.channelsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val callElapsedSec by viewModel.callElapsedSec.collectAsStateWithLifecycle()
    val micLevel by viewModel.audioCallEngine.micLevel.collectAsStateWithLifecycle()
    val isMuted by viewModel.audioCallEngine.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.audioCallEngine.isSpeakerOn.collectAsStateWithLifecycle()

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val allChats = (chatsState as? UiState.Success)?.data ?: emptyList()
    val totalUnread = allChats.count { !it.archived && it.unreadCount > 0L }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { navController.navigate(ROUTE_SETTINGS) },
                                        modifier = Modifier.testTag("open_settings_button")
                                    ) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Open Settings")
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    val tabs = listOf(
                                        Triple("Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
                                        Triple("Updates", Icons.Filled.Update, Icons.Outlined.Update),
                                        Triple("Calls", Icons.Filled.Call, Icons.Outlined.Call),
                                        Triple("Sync", Icons.Filled.Devices, Icons.Outlined.Devices)
                                    )
                                    tabs.forEachIndexed { idx, (label, filled, outlined) ->
                                        NavigationBarItem(
                                            selected = selectedTab == idx,
                                            onClick = { selectedTab = idx },
                                            icon = {
                                                BadgedBox(
                                                    badge = {
                                                        if (idx == 0 && totalUnread > 0) {
                                                            Badge { Text(totalUnread.toString()) }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (selectedTab == idx) filled else outlined,
                                                        contentDescription = label
                                                    )
                                                }
                                            },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("nav_tab_$idx")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            if (useRail) {
                                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                    val tabs = listOf(
                                        Triple("Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
                                        Triple("Updates", Icons.Filled.Update, Icons.Outlined.Update),
                                        Triple("Calls", Icons.Filled.Call, Icons.Outlined.Call),
                                        Triple("Sync", Icons.Filled.Devices, Icons.Outlined.Devices)
                                    )
                                    tabs.forEachIndexed { idx, (label, filled, outlined) ->
                                        NavigationRailItem(
                                            selected = selectedTab == idx,
                                            onClick = { selectedTab = idx },
                                            icon = {
                                                BadgedBox(
                                                    badge = {
                                                        if (idx == 0 && totalUnread > 0) {
                                                            Badge { Text(totalUnread.toString()) }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (selectedTab == idx) filled else outlined,
                                                        contentDescription = label
                                                    )
                                                }
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                when (selectedTab) {
                                    0 -> ChatsTabScreen(
                                        chatsState = chatsState,
                                        contacts = contacts,
                                        currentUserId = viewModel.currentUserId,
                                        compactMode = localPrefs.compactChatList,
                                        draftsMap = localPrefs.draftsMap,
                                        hasChatLockPin = localPrefs.chatLockPinHash.isNotBlank(),
                                        onVerifyChatPin = viewModel::verifyChatLockPin,
                                        onOpenChat = { chatId ->
                                            allChats.firstOrNull { it.id == chatId }?.let { viewModel.clearUnread(it) }
                                            viewModel.selectChat(chatId)
                                            navController.navigate("chat_detail/$chatId")
                                        },
                                        onCreateChat = { title, type, peerUid, extras, desc ->
                                            viewModel.createNewChat(title, type, peerUid, extras, desc) { newId ->
                                                viewModel.selectChat(newId)
                                                navController.navigate("chat_detail/$newId")
                                            }
                                        },
                                        onSendBroadcast = viewModel::sendBroadcastMessage,
                                        onAddContact = viewModel::addContactByUid,
                                        onToggleContactFavorite = viewModel::toggleContactFavorite,
                                        onToggleContactBlocked = viewModel::toggleContactBlocked,
                                        onDeleteContact = viewModel::deleteContact,
                                        onTogglePin = viewModel::togglePinChat,
                                        onToggleArchive = viewModel::toggleArchiveChat,
                                        onToggleMute = viewModel::toggleMuteChat,
                                        onToggleLock = viewModel::toggleLockChat,
                                        onToggleUnread = viewModel::toggleMarkUnread,
                                        onDeleteChat = viewModel::deleteChatThread
                                    )
                                    1 -> UpdatesTabScreen(
                                        statuses = statuses,
                                        channels = channels,
                                        onPostStatus = viewModel::postStatusUpdate,
                                        onReactStatus = viewModel::reactToStatus,
                                        onDeleteStatus = viewModel::deleteStatusUpdate,
                                        onCreateChannel = viewModel::createChannel,
                                        onPostChannelUpdate = viewModel::postChannelUpdate,
                                        onToggleChannelMute = viewModel::toggleChannelMute,
                                        onDeleteChannel = viewModel::deleteChannel
                                    )
                                    2 -> CallsTabScreen(
                                        callsState = callsState,
                                        contacts = contacts,
                                        onStartVoiceCall = viewModel::startVoiceCall,
                                        onCreateCallLink = viewModel::createCallInviteLink,
                                        onDeleteCall = viewModel::deleteCallRecord
                                    )
                                    else -> SyncVaultTabScreen(
                                        devices = devices,
                                        backups = backups,
                                        autoBackupFreq = localPrefs.autoBackupFrequency,
                                        onLinkDevice = viewModel::linkCompanionDevice,
                                        onSyncHandoff = viewModel::syncHandoffAcrossDevices,
                                        onRevokeDevice = viewModel::revokeLinkedDevice,
                                        onCreateBackup = viewModel::createCloudBackupNow,
                                        onAuditBackup = viewModel::auditCloudBackup,
                                        onDeleteBackup = viewModel::deleteCloudBackup,
                                        onSetAutoBackupFreq = viewModel::setAutoBackupFreq
                                    )
                                }
                            }
                        }
                    }
                }
            }

            composable(
                route = ROUTE_CHAT_DETAIL,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                val chat = allChats.firstOrNull { it.id == chatId }
                if (chat != null) {
                    ChatDetailScreen(
                        chat = chat,
                        allChats = allChats,
                        contacts = contacts,
                        messagesState = messagesState,
                        aiState = aiState,
                        currentUserId = viewModel.currentUserId,
                        prefs = localPrefs,
                        aiEnabled = userProfile?.aiSmartRepliesEnabled ?: true,
                        isDarkTheme = isDarkTheme,
                        onBack = { navController.popBackStack() },
                        onSendMessage = { text, type, reply, media, voiceSec, viewOnce ->
                            viewModel.sendMessage(chat, text, type, reply, media, voiceSec, viewOnce = viewOnce)
                        },
                        onForwardMessage = viewModel::forwardEncryptedMessage,
                        onSaveDraft = { draft -> viewModel.saveChatDraft(chat.id, draft) },
                        onPinMessage = { preview -> viewModel.pinMessageInChat(chat, preview) },
                        onRevealViewOnce = { msgId -> viewModel.revealViewOnce(chat.id, msgId) },
                        onVotePoll = { msg, idx -> viewModel.voteOnPoll(chat.id, msg, idx) },
                        onReactOrStar = { msg, reaction, starred ->
                            viewModel.reactOrStarMessage(chat.id, msg, reaction, starred)
                        },
                        onEditMessage = { msgId, newText -> viewModel.editMessage(chat, msgId, newText) },
                        onDeleteMessage = { msgId -> viewModel.deleteMessageForAll(chat, msgId) },
                        onUpdateThreadInfo = { t, d -> viewModel.updateChatInfo(chat, t, d) },
                        onToggleLockChat = { viewModel.toggleLockChat(chat) },
                        onStartVoiceCall = { hasMic ->
                            viewModel.startVoiceCall(
                                calleeId = chat.memberUids.firstOrNull { it != viewModel.currentUserId } ?: "echo_test",
                                calleeName = chat.title,
                                hasMicPermission = hasMic,
                                isEchoTest = chat.memberUids.size <= 1
                            )
                        },
                        onSetDisappearingHours = { hrs -> viewModel.setDisappearingHours(chat, hrs) },
                        onToggleVerifyE2ee = { viewModel.verifyChatEncryption(chat) },
                        onRequestSmartReplies = { msgs -> viewModel.requestSmartReplies(chat, msgs) },
                        onRewriteDraft = viewModel::rewriteDraftWithAi,
                        onComposeFromTopic = viewModel::composeDraftFromTopic,
                        onTranslateText = viewModel::translateMessageWithAi,
                        onSummarizeThread = { msgs -> viewModel.summarizeChatThread(chat, msgs) },
                        onResetAiState = viewModel::resetAiState
                    )
                }
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    profile = userProfile,
                    prefs = localPrefs,
                    onBack = { navController.popBackStack() },
                    onUpdateProfile = viewModel::updateProfileField,
                    onDarkModeChange = viewModel::updateDarkMode,
                    onScreenshotGuardChange = viewModel::setScreenshotGuard,
                    onCompactModeChange = viewModel::setCompactChatList,
                    onWallpaperChange = viewModel::setWallpaperEnabled,
                    onWallpaperPresetChange = viewModel::setWallpaperPreset,
                    onFontSizeScaleChange = viewModel::setFontSizeScale,
                    onEnterToSendChange = viewModel::setEnterToSend,
                    onCallNoiseSuppressionChange = viewModel::setCallNoiseSuppression,
                    onAiToneChange = viewModel::setAiReplyTone,
                    onSetChatLockPin = viewModel::setChatLockPin,
                    onAddQuickReply = viewModel::addQuickReplyTemplate,
                    onSignOut = onSignOut
                )
            }
        }

        activeCall?.let { call ->
            ActiveCallScreen(
                call = call,
                elapsedSec = callElapsedSec,
                micLevel = micLevel,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                onToggleMute = viewModel.audioCallEngine::toggleMute,
                onToggleSpeaker = viewModel.audioCallEngine::toggleSpeaker,
                onMicPermissionGranted = { viewModel.refreshMicCaptureForActiveCall(true) },
                onEndCall = viewModel::endActiveCall
            )
        }
    }
}
