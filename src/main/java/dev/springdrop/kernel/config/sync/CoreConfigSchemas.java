package dev.springdrop.kernel.config.sync;

import dev.springdrop.kernel.action.ActionConfig;
import dev.springdrop.kernel.ban.BannedAddresses;
import dev.springdrop.kernel.block.BlockPlacement;
import dev.springdrop.kernel.cache.PerformanceSettings;
import dev.springdrop.kernel.cron.AutomatedCronSettings;
import dev.springdrop.kernel.datetime.DateFormat;
import dev.springdrop.kernel.datetime.SiteDateSettings;
import dev.springdrop.kernel.entity.TranslatableFields;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.file.FileSystemSettings;
import dev.springdrop.kernel.filter.TextFormat;
import dev.springdrop.kernel.image.ImageStyle;
import dev.springdrop.kernel.image.ResponsiveImageStyle;
import dev.springdrop.kernel.jsonapi.JsonApiSettings;
import dev.springdrop.kernel.language.ConfigTranslation;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageSettings;
import dev.springdrop.kernel.language.NegotiationSettings;
import dev.springdrop.kernel.layout.LayoutBuilderDisplay;
import dev.springdrop.kernel.log.LoggingSettings;
import dev.springdrop.kernel.mail.MailSettings;
import dev.springdrop.kernel.maintenance.MaintenanceSettings;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.moderation.ModerationConfig;
import dev.springdrop.kernel.path.AliasPattern;
import dev.springdrop.kernel.rest.RestResourceConfig;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.search.SearchPage;
import dev.springdrop.kernel.search.SearchSettings;
import dev.springdrop.kernel.site.RssSettings;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.user.UserSettings;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.web.ErrorPagesConfig;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import java.util.List;
import org.springframework.stereotype.Component;

/** The schemas of core's config objects that are not config entities. */
@Component
public class CoreConfigSchemas implements ConfigSchemaProvider {

    @Override
    public List<ConfigSchema> schemas() {
        return List.of(
                ConfigSchema.under(RoleConfig.CONFIG_PREFIX, RoleConfig.class),
                ConfigSchema.under(TranslatableFields.CONFIG_PREFIX, TranslatableFields.Settings.class),
                ConfigSchema.under(LayoutBuilderDisplay.CONFIG_PREFIX, LayoutBuilderDisplay.class),
                ConfigSchema.under(AliasPattern.CONFIG_PREFIX, AliasPattern.class),
                ConfigSchema.under(ConfigTranslation.CONFIG_PREFIX, ConfigTranslation.class),
                ConfigSchema.under(LanguageConfig.CONFIG_PREFIX, LanguageConfig.class),
                ConfigSchema.under(FieldInstanceConfig.CONFIG_PREFIX, FieldInstanceConfig.class),
                ConfigSchema.under(FieldStorageConfig.CONFIG_PREFIX, FieldStorageConfig.class),
                ConfigSchema.under(FormDisplayConfig.CONFIG_PREFIX, FormDisplayConfig.class),
                ConfigSchema.under(ViewDisplayConfig.CONFIG_PREFIX, ViewDisplayConfig.class),
                ConfigSchema.under(ImageStyle.CONFIG_PREFIX, ImageStyle.class),
                ConfigSchema.under(ResponsiveImageStyle.CONFIG_PREFIX, ResponsiveImageStyle.class),
                ConfigSchema.under(SearchPage.CONFIG_PREFIX, SearchPage.class),
                ConfigSchema.under(ActionConfig.CONFIG_PREFIX, ActionConfig.class),
                ConfigSchema.under(MenuConfig.CONFIG_PREFIX, MenuConfig.class),
                ConfigSchema.under(WorkflowConfig.CONFIG_PREFIX, WorkflowConfig.class),
                ConfigSchema.under(TextFormat.CONFIG_PREFIX, TextFormat.class),
                ConfigSchema.under(ViewConfig.CONFIG_PREFIX, ViewConfig.class),
                ConfigSchema.under(RestResourceConfig.CONFIG_PREFIX, RestResourceConfig.class),
                ConfigSchema.under(BlockPlacement.CONFIG_PREFIX, BlockPlacement.class),
                ConfigSchema.under(ModerationConfig.CONFIG_PREFIX, ModerationConfig.class),
                ConfigSchema.under(DateFormat.CONFIG_PREFIX, DateFormat.class),
                ConfigSchema.named(SiteDateSettings.CONFIG_NAME, SiteDateSettings.class),
                ConfigSchema.named(JsonApiSettings.CONFIG_NAME, JsonApiSettings.class),
                ConfigSchema.named(ErrorPagesConfig.CONFIG_NAME, ErrorPagesConfig.class),
                ConfigSchema.named(MailSettings.CONFIG_NAME, MailSettings.class),
                ConfigSchema.named(NegotiationSettings.CONFIG_NAME, NegotiationSettings.class),
                ConfigSchema.named(UserSettings.CONFIG_NAME, UserSettings.class),
                ConfigSchema.named(LanguageSettings.CONFIG_NAME, LanguageSettings.class),
                ConfigSchema.named(SearchSettings.CONFIG_NAME, SearchSettings.class),
                ConfigSchema.named(LoggingSettings.CONFIG_NAME, LoggingSettings.class),
                ConfigSchema.named(SiteInformation.CONFIG_NAME, SiteInformation.class),
                ConfigSchema.named(RssSettings.CONFIG_NAME, RssSettings.class),
                ConfigSchema.named(PerformanceSettings.CONFIG_NAME, PerformanceSettings.class),
                ConfigSchema.named(FileSystemSettings.CONFIG_NAME, FileSystemSettings.class),
                ConfigSchema.named(MaintenanceSettings.CONFIG_NAME, MaintenanceSettings.class),
                ConfigSchema.named(BannedAddresses.CONFIG_NAME, BannedAddresses.class),
                ConfigSchema.named(AutomatedCronSettings.CONFIG_NAME, AutomatedCronSettings.class));
    }
}
