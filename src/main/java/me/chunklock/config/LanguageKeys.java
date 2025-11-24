package me.chunklock.config;

/**
 * Centralized constants for all language keys used throughout the plugin.
 * This ensures type safety and prevents typos when referencing language keys.
 * 
 * @author Chunklock Team
 * @version 2.0.0
 */
public final class LanguageKeys {
    
    private LanguageKeys() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    // ===== COMMAND MESSAGES =====
    
    public static final String COMMAND_UNLOCK_USAGE = "commands.unlock.usage";
    public static final String COMMAND_UNLOCK_USAGE_DETAILS = "commands.unlock.usage-details";
    public static final String COMMAND_UNLOCK_CONSOLE_ERROR = "commands.unlock.console-error";
    public static final String COMMAND_UNLOCK_PLAYER_NOT_FOUND = "commands.unlock.player-not-found";
    public static final String COMMAND_UNLOCK_WORLD_NOT_FOUND = "commands.unlock.world-not-found";
    public static final String COMMAND_UNLOCK_INVALID_COORDS = "commands.unlock.invalid-coords";
    public static final String COMMAND_UNLOCK_ALREADY_UNLOCKED = "commands.unlock.already-unlocked";
    public static final String COMMAND_UNLOCK_SUCCESS_ADMIN = "commands.unlock.success-admin";
    public static final String COMMAND_UNLOCK_SUCCESS_PLAYER = "commands.unlock.success-player";
    public static final String COMMAND_UNLOCK_ERROR = "commands.unlock.error";
    
    public static final String COMMAND_HELP_TITLE = "commands.help.title";
    public static final String COMMAND_HELP_BASIC_HEADER = "commands.help.basic-header";
    public static final String COMMAND_HELP_STATUS = "commands.help.status";
    public static final String COMMAND_HELP_SPAWN = "commands.help.spawn";
    public static final String COMMAND_HELP_TEAM = "commands.help.team";
    public static final String COMMAND_HELP_TIP = "commands.help.tip";
    public static final String COMMAND_HELP_ADMIN_HEADER = "commands.help.admin-header";
    public static final String COMMAND_HELP_UNLOCK = "commands.help.unlock";
    public static final String COMMAND_HELP_RESET = "commands.help.reset";
    public static final String COMMAND_HELP_BYPASS = "commands.help.bypass";
    public static final String COMMAND_HELP_RELOAD = "commands.help.reload";
    public static final String COMMAND_HELP_DETAILED = "commands.help.detailed";
    public static final String COMMAND_HELP_TEAM_TIP = "commands.help.team-tip";
    public static final String COMMAND_HELP_UNKNOWN = "commands.help.unknown";
    public static final String COMMAND_HELP_NO_PERMISSION = "commands.help.no-permission";
    public static final String COMMAND_HELP_COMMAND_HELP = "commands.help.command-help";
    public static final String COMMAND_HELP_USAGE = "commands.help.usage";
    public static final String COMMAND_HELP_DESCRIPTION = "commands.help.description";
    public static final String COMMAND_HELP_PERMISSION = "commands.help.permission";
    
    public static final String COMMAND_STATUS_TITLE = "commands.status.title";
    public static final String COMMAND_STATUS_CHUNKS = "commands.status.chunks";
    public static final String COMMAND_STATUS_SPAWN = "commands.status.spawn";
    public static final String COMMAND_STATUS_TEAM = "commands.status.team";
    
    public static final String COMMAND_RESET_CONFIRM = "commands.reset.confirm";
    public static final String COMMAND_RESET_SUCCESS = "commands.reset.success";
    public static final String COMMAND_RESET_ERROR = "commands.reset.error";
    
    public static final String COMMAND_RELOAD_SUCCESS = "commands.reload.success";
    public static final String COMMAND_RELOAD_ERROR = "commands.reload.error";
    
    // ===== GUI MESSAGES =====
    
    public static final String GUI_UNLOCK_TITLE = "gui.unlock.title";
    public static final String GUI_UNLOCK_HELP_TITLE = "gui.unlock.help-title";
    public static final String GUI_UNLOCK_HELP_PROCESS_TITLE = "gui.unlock.help-process-title";
    public static final String GUI_UNLOCK_HELP_STEP_1 = "gui.unlock.help-step-1";
    public static final String GUI_UNLOCK_HELP_STEP_2 = "gui.unlock.help-step-2";
    public static final String GUI_UNLOCK_HELP_STEP_3 = "gui.unlock.help-step-3";
    public static final String GUI_UNLOCK_HELP_TIPS_TITLE = "gui.unlock.help-tips-title";
    public static final String GUI_UNLOCK_HELP_TIP_1 = "gui.unlock.help-tip-1";
    public static final String GUI_UNLOCK_HELP_TIP_2 = "gui.unlock.help-tip-2";
    public static final String GUI_UNLOCK_HELP_TIP_3 = "gui.unlock.help-tip-3";
    public static final String GUI_UNLOCK_HELP_TIP_4 = "gui.unlock.help-tip-4";
    
    public static final String GUI_UNLOCK_CONTESTED_TITLE = "gui.unlock.contested-title";
    public static final String GUI_UNLOCK_CONTESTED_MULTIPLIER = "gui.unlock.contested-multiplier";
    
    public static final String GUI_UNLOCK_SESSION_EXPIRED = "gui.unlock.session-expired";
    public static final String GUI_UNLOCK_ALREADY_UNLOCKED = "gui.unlock.already-unlocked";
    public static final String GUI_UNLOCK_CONTESTED_LIMIT = "gui.unlock.contested-limit";
    public static final String GUI_UNLOCK_CONTESTED_LIMIT_TIP = "gui.unlock.contested-limit-tip";
    public static final String GUI_UNLOCK_MISSING_ITEM = "gui.unlock.missing-item";
    public static final String GUI_UNLOCK_ERROR = "gui.unlock.error";
    
    public static final String GUI_UNLOCK_INSUFFICIENT_FUNDS_TITLE = "gui.unlock.insufficient-funds-title";
    public static final String GUI_UNLOCK_INSUFFICIENT_FUNDS_REQUIRED = "gui.unlock.insufficient-funds-required";
    public static final String GUI_UNLOCK_INSUFFICIENT_FUNDS_BALANCE = "gui.unlock.insufficient-funds-balance";
    public static final String GUI_UNLOCK_INSUFFICIENT_FUNDS_MISSING = "gui.unlock.insufficient-funds-missing";
    public static final String GUI_UNLOCK_INSUFFICIENT_FUNDS_TIP = "gui.unlock.insufficient-funds-tip";
    public static final String GUI_UNLOCK_INSUFFICIENT_FUNDS_ACTION = "gui.unlock.insufficient-funds-action";
    
    public static final String GUI_UNLOCK_PAYMENT_FAILED = "gui.unlock.payment-failed";
    public static final String GUI_UNLOCK_PAYMENT_PROCESSING_FAILED = "gui.unlock.payment-processing-failed";
    
    public static final String GUI_UNLOCK_INSUFFICIENT_RESOURCES_TITLE = "gui.unlock.insufficient-resources-title";
    public static final String GUI_UNLOCK_INSUFFICIENT_RESOURCES_REQUIRED = "gui.unlock.insufficient-resources-required";
    public static final String GUI_UNLOCK_INSUFFICIENT_RESOURCES_HAVE = "gui.unlock.insufficient-resources-have";
    public static final String GUI_UNLOCK_INSUFFICIENT_RESOURCES_MISSING = "gui.unlock.insufficient-resources-missing";
    public static final String GUI_UNLOCK_INSUFFICIENT_RESOURCES_TIP = "gui.unlock.insufficient-resources-tip";
    
    public static final String GUI_UNLOCK_FAILED_COMPLETE = "gui.unlock.failed-complete";
    
    public static final String GUI_UNLOCK_SUCCESS_TITLE = "gui.unlock.success-title";
    public static final String GUI_UNLOCK_SUCCESS_LOCATION = "gui.unlock.success-location";
    public static final String GUI_UNLOCK_SUCCESS_BIOME = "gui.unlock.success-biome";
    public static final String GUI_UNLOCK_SUCCESS_CONSUMED = "gui.unlock.success-consumed";
    public static final String GUI_UNLOCK_SUCCESS_CONTESTED = "gui.unlock.success-contested";
    public static final String GUI_UNLOCK_SUCCESS_MESSAGE = "gui.unlock.success-message";
    
    public static final String GUI_BUILDER_CHUNK_INFO_TITLE = "gui.builder.chunk-info-title";
    public static final String GUI_BUILDER_CHUNK_INFO_LOCATION = "gui.builder.chunk-info-location";
    public static final String GUI_BUILDER_CHUNK_INFO_BIOME = "gui.builder.chunk-info-biome";
    public static final String GUI_BUILDER_CHUNK_INFO_DIFFICULTY = "gui.builder.chunk-info-difficulty";
    public static final String GUI_BUILDER_CHUNK_INFO_SCORE = "gui.builder.chunk-info-score";
    public static final String GUI_BUILDER_CHUNK_INFO_DIFFICULTY_NOTE = "gui.builder.chunk-info-difficulty-note";
    public static final String GUI_BUILDER_CHUNK_INFO_DIFFICULTY_NOTE_2 = "gui.builder.chunk-info-difficulty-note-2";
    
    public static final String GUI_BUILDER_PROGRESS_TITLE = "gui.builder.progress-title";
    public static final String GUI_BUILDER_PROGRESS_ITEMS = "gui.builder.progress-items";
    public static final String GUI_BUILDER_PROGRESS_NEED = "gui.builder.progress-need";
    
    public static final String GUI_BUILDER_REQUIRED_TITLE = "gui.builder.required-title";
    public static final String GUI_BUILDER_REQUIRED_TOTAL = "gui.builder.required-total";
    public static final String GUI_BUILDER_REQUIRED_HAVE = "gui.builder.required-have";
    public static final String GUI_BUILDER_REQUIRED_ALL = "gui.builder.required-all";
    public static final String GUI_BUILDER_REQUIRED_MISSING = "gui.builder.required-missing";
    public static final String GUI_BUILDER_REQUIRED_TIP = "gui.builder.required-tip";
    public static final String GUI_BUILDER_REQUIRED_ALL_ITEMS = "gui.builder.required-all-items";
    public static final String GUI_BUILDER_REQUIRED_ENOUGH = "gui.builder.required-enough";
    
    public static final String GUI_BUILDER_UNLOCK_BUTTON_READY = "gui.builder.unlock-button-ready";
    public static final String GUI_BUILDER_UNLOCK_BUTTON_REQUIREMENTS_MET = "gui.builder.unlock-button-requirements-met";
    public static final String GUI_BUILDER_UNLOCK_BUTTON_CONSUME = "gui.builder.unlock-button-consume";
    public static final String GUI_BUILDER_UNLOCK_BUTTON_CLICK = "gui.builder.unlock-button-click";
    public static final String GUI_BUILDER_UNLOCK_BUTTON_NOT_READY = "gui.builder.unlock-button-not-ready";
    public static final String GUI_BUILDER_UNLOCK_BUTTON_MISSING = "gui.builder.unlock-button-missing";
    public static final String GUI_BUILDER_UNLOCK_BUTTON_NEED_MORE = "gui.builder.unlock-button-need-more";
    public static final String GUI_BUILDER_UNLOCK_BUTTON_GATHER = "gui.builder.unlock-button-gather";
    
    public static final String GUI_BUILDER_HELP_TITLE = "gui.builder.help-title";
    public static final String GUI_BUILDER_HELP_PROCESS_TITLE = "gui.builder.help-process-title";
    public static final String GUI_BUILDER_HELP_STEP_1 = "gui.builder.help-step-1";
    public static final String GUI_BUILDER_HELP_STEP_2 = "gui.builder.help-step-2";
    public static final String GUI_BUILDER_HELP_STEP_3 = "gui.builder.help-step-3";
    public static final String GUI_BUILDER_HELP_TIPS_TITLE = "gui.builder.help-tips-title";
    public static final String GUI_BUILDER_HELP_TIP_1 = "gui.builder.help-tip-1";
    public static final String GUI_BUILDER_HELP_TIP_2 = "gui.builder.help-tip-2";
    public static final String GUI_BUILDER_HELP_TIP_3 = "gui.builder.help-tip-3";
    
    public static final String GUI_BUILDER_TEAM_TITLE = "gui.builder.team-title";
    public static final String GUI_BUILDER_TEAM_UNLOCK = "gui.builder.team-unlock";
    public static final String GUI_BUILDER_TEAM_RESOURCES = "gui.builder.team-resources";
    
    public static final String GUI_BUILDER_MONEY_PROGRESS_TITLE = "gui.builder.money-progress-title";
    public static final String GUI_BUILDER_MONEY_PROGRESS_BALANCE = "gui.builder.money-progress-balance";
    public static final String GUI_BUILDER_MONEY_PROGRESS_REQUIRED = "gui.builder.money-progress-required";
    public static final String GUI_BUILDER_MONEY_PROGRESS_NEED = "gui.builder.money-progress-need";
    
    public static final String GUI_BUILDER_MONEY_REQUIRED_TITLE = "gui.builder.money-required-title";
    public static final String GUI_BUILDER_MONEY_REQUIRED_MODE = "gui.builder.money-required-mode";
    public static final String GUI_BUILDER_MONEY_REQUIRED_CURRENCY = "gui.builder.money-required-currency";
    public static final String GUI_BUILDER_MONEY_REQUIRED_CAN_AFFORD = "gui.builder.money-required-can-afford";
    public static final String GUI_BUILDER_MONEY_REQUIRED_CANNOT_AFFORD = "gui.builder.money-required-cannot-afford";
    public static final String GUI_BUILDER_MONEY_REQUIRED_CLICK = "gui.builder.money-required-click";
    
    public static final String GUI_BUILDER_MONEY_UNLOCK_BUTTON_READY = "gui.builder.money-unlock-button-ready";
    public static final String GUI_BUILDER_MONEY_UNLOCK_BUTTON_NOT_READY = "gui.builder.money-unlock-button-not-ready";
    public static final String GUI_BUILDER_MONEY_UNLOCK_BUTTON_CLICK = "gui.builder.money-unlock-button-click";
    public static final String GUI_BUILDER_MONEY_UNLOCK_BUTTON_CLICK_2 = "gui.builder.money-unlock-button-click-2";
    public static final String GUI_BUILDER_MONEY_UNLOCK_BUTTON_BALANCE = "gui.builder.money-unlock-button-balance";
    public static final String GUI_BUILDER_MONEY_UNLOCK_BUTTON_NEED = "gui.builder.money-unlock-button-need";
    
    public static final String GUI_BUILDER_STACK_PART = "gui.builder.stack-part";
    public static final String GUI_BUILDER_STACK_MORE = "gui.builder.stack-more";
    
    // ===== HOLOGRAM MESSAGES =====
    
    public static final String HOLOGRAM_LOCKED_TITLE = "holograms.locked-title";
    public static final String HOLOGRAM_MATERIAL_LINE = "holograms.material-line";
    public static final String HOLOGRAM_STATUS_HAVE = "holograms.status-have";
    public static final String HOLOGRAM_STATUS_MISSING = "holograms.status-missing";
    public static final String HOLOGRAM_CLICK_TO_UNLOCK = "holograms.click-to-unlock";
    public static final String HOLOGRAM_COST_LINE = "holograms.cost-line";
    public static final String HOLOGRAM_CAN_AFFORD = "holograms.can-afford";
    public static final String HOLOGRAM_CANNOT_AFFORD = "holograms.cannot-afford";
    
    // ===== ERROR MESSAGES =====
    
    public static final String ERROR_PLAYER_NOT_FOUND = "errors.player-not-found";
    public static final String ERROR_CHUNK_ALREADY_UNLOCKED = "errors.chunk-already-unlocked";
    public static final String ERROR_INSUFFICIENT_FUNDS = "errors.insufficient-funds";
    public static final String ERROR_MISSING_ITEMS = "errors.missing-items";
    public static final String ERROR_NO_PERMISSION = "errors.no-permission";
    public static final String ERROR_INVALID_CHUNK = "errors.invalid-chunk";
    public static final String ERROR_NOT_ADJACENT = "errors.not-adjacent";
    public static final String ERROR_GENERIC = "errors.generic";
    
    // ===== SUCCESS MESSAGES =====
    
    public static final String SUCCESS_CHUNK_UNLOCKED = "success.chunk-unlocked";
    public static final String SUCCESS_PAYMENT_PROCESSED = "success.payment-processed";
    
    // ===== ECONOMY MESSAGES =====
    
    public static final String ECONOMY_COST_DISPLAY = "economy.cost-display";
    public static final String ECONOMY_BALANCE_DISPLAY = "economy.balance-display";
    public static final String ECONOMY_MISSING_AMOUNT = "economy.missing-amount";
    
    // ===== TEAM MESSAGES =====
    
    public static final String TEAM_NOT_IN_TEAM = "team.not-in-team";
    public static final String TEAM_NO_PERMISSION = "team.no-permission";
    public static final String TEAM_JOIN_REQUEST_ACCEPTED = "team.join-request-accepted";
    public static final String TEAM_JOIN_REQUEST_DENIED = "team.join-request-denied";
    public static final String TEAM_JOIN_REQUEST_NO_PENDING = "team.join-request-no-pending";
    public static final String TEAM_CHAT_DISABLED = "team.chat-disabled";
    public static final String TEAM_CHAT_FORMAT = "team.chat-format";
    public static final String TEAM_PLAYER_JOINED = "team.player-joined";
    public static final String TEAM_JOIN_ACCEPTED_ADMIN = "team.join-accepted-admin";
    public static final String TEAM_JOIN_DENIED_ADMIN = "team.join-denied-admin";
    
    // ===== UNLOCK MESSAGES =====
    
    public static final String UNLOCK_STARTING_CHUNK_ASSIGNED = "unlock.starting-chunk-assigned";
    public static final String UNLOCK_SPAWN_COORDS = "unlock.spawn-coords";
    public static final String UNLOCK_CHUNK_INFO = "unlock.chunk-info";
    
    // ===== PROTECTION MESSAGES =====
    
    public static final String PROTECTION_CHUNK_LOCKED = "protection.chunk-locked";
    public static final String PROTECTION_NOT_OWNER = "protection.not-owner";
    public static final String PROTECTION_TEAM_MEMBER = "protection.team-member";
}

