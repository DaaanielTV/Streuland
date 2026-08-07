package de.streuland;

import de.streuland.admin.AdminPlotService;
import de.streuland.admin.BlockChangeLogger;
import de.streuland.admin.DailyPlotBackupService;
import de.streuland.admin.StreulandDiagnosticsService;
import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.approval.PlotApprovalService;
import de.streuland.backup.PlotBackupCoordinator;
import de.streuland.backup.SnapshotService;
import de.streuland.bootstrap.ConfigValidationService;
import de.streuland.bootstrap.FeatureToggles;
import de.streuland.clan.ClanManager;
import de.streuland.clan.ClanStorage;
import de.streuland.command.DistrictCommandExecutor;
import de.streuland.command.PlotCommandExecutor;
import de.streuland.command.StreulandCommandExecutor;
import de.streuland.commands.PlotApprovalCommand;
import de.streuland.commands.PlotPriceCommand;
import de.streuland.commands.PlotUpgradeCommand;
import de.streuland.compat.WorldGuardCompat;
import de.streuland.dashboard.DashboardDataExporter;
import de.streuland.dashboard.PlotAuditLogService;
import de.streuland.dashboard.RestApiController;
import de.streuland.discord.DiscordNotifier;
import de.streuland.district.DistrictClusterService;
import de.streuland.district.DistrictManager;
import de.streuland.district.DistrictProgressService;
import de.streuland.district.TraderNpcService;
import de.streuland.flags.PlotFlagManager;
import de.streuland.history.JournalManager;
import de.streuland.history.PlotChangeJournal;
import de.streuland.i18n.MessageProvider;
import de.streuland.listener.BlockChangeListener;
import de.streuland.listener.ProtectionListener;
import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.neighborhood.ResourceSyncScheduler;
import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import de.streuland.plot.biome.BiomeBonusService;
import de.streuland.plot.biome.BiomeEffectScheduler;
import de.streuland.plot.market.PlotMarketService;
import de.streuland.plot.skin.PlotSkinService;
import de.streuland.plot.snapshot.SnapshotManager;
import de.streuland.plot.snapshot.SnapshotStorage;
import de.streuland.plot.template.PlotTemplateRegistry;
import de.streuland.plot.upgrade.DefaultPlotUpgradeService;
import de.streuland.plot.upgrade.PlotOwnershipResolver;
import de.streuland.plot.upgrade.PlotStorageBackedUpgradeStorage;
import de.streuland.plot.upgrade.PlotUpgradeService;
import de.streuland.plot.upgrade.PlotUpgradeTree;
import de.streuland.plot.upgrade.YamlPlotUpgradeCatalog;
import de.streuland.pricing.PricingEngine;
import de.streuland.quest.QuestService;
import de.streuland.quest.QuestTracker;
import de.streuland.rules.DefaultPlotLevelProvider;
import de.streuland.rules.ExampleRules;
import de.streuland.rules.RuleEngine;
import de.streuland.rules.listener.RuleListener;
import de.streuland.schematic.SchematicLoader;
import de.streuland.storage.SqlitePlotStorage;
import de.streuland.storage.YamlPlotStorage;
import de.streuland.transaction.TransactionManager;
import de.streuland.warp.CooldownManager;
import de.streuland.warp.PortalManager;
import de.streuland.weather.ParticleEffectScheduler;
import de.streuland.weather.SeasonalEffectListener;
import de.streuland.weather.SeasonalWeatherService;
import de.streuland.web.AdminObservabilityService;
import de.streuland.web.WebServer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;

public class StreulandPlugin extends JavaPlugin {
    private FeatureToggles features;
    private PlotManager plotManager;
    private PathGenerator pathGenerator;
    private SnapshotManager snapshotManager;
    private RuleEngine ruleEngine;
    private PlotSkinService plotSkinService;
    private BiomeBonusService biomeBonusService;
    private BiomeEffectScheduler biomeEffectScheduler;
    private InMemoryPlotAnalyticsService analyticsService;
    private NeighborhoodService neighborhoodService;
    private ResourceSyncScheduler resourceSyncScheduler;
    private RestApiController restApiController;
    private QuestService questService;
    private QuestTracker questTracker;
    private PlotMarketService plotMarketService;
    private PricingEngine pricingEngine;
    private PlotPriceCommand plotPriceCommand;
    private Economy economy;
    private de.streuland.economy.PlotEconomyHook plotEconomyHook;
    private BlockChangeLogger blockChangeLogger;
    private AdminPlotService adminPlotService;
    private DailyPlotBackupService dailyPlotBackupService;
    private SnapshotService snapshotService;
    private PlotBackupCoordinator plotBackupCoordinator;
    private PlotAuditLogService plotAuditLogService;
    private TraderNpcService traderNpcService;
    private SeasonalWeatherService seasonalWeatherService;
    private ParticleEffectScheduler particleEffectScheduler;
    private SeasonalEffectListener seasonalEffectListener;
    private PlotFlagManager plotFlagManager;
    private PlotTemplateRegistry plotTemplateRegistry;
    private PlotUpgradeService plotUpgradeService;
    private WorldGuardCompat worldGuardCompat;
    private MessageProvider messageProvider;
    private DiscordNotifier discordNotifier;
    private PlotApprovalService plotApprovalService;
    private TransactionManager transactionManager;
    private JournalManager journalManager;
    private PlotChangeJournal plotChangeJournal;
    private ProtectionListener protectionListener;
    private BlockChangeListener blockChangeListener;
    private RuleListener ruleListener;
    private PortalManager portalManager;
    private WebServer webServer;
    private de.streuland.storage.PlotStorage configuredStorageAdapter;
    private ClanManager clanManager;
    private ClanStorage clanStorage;
    private DistrictManager districtManager;
    private DistrictProgressService districtProgressService;
    private de.streuland.movement.MovementPassService movementPassService;
    private de.streuland.movement.MovementGuard movementGuard;
    private de.streuland.clan.ClanShaftService clanShaftService;
    private de.streuland.marketstand.MarketStandService marketStandService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            features = FeatureToggles.fromConfig(getConfig());
            validateStartupConfiguration();
            initializeStorageAdapter();

            messageProvider = new MessageProvider(this);
            plotManager = new PlotManager(this);
            getLogger().info("✓ PlotManager initialized");

            plotTemplateRegistry = new PlotTemplateRegistry(new SchematicLoader(this));
            plotTemplateRegistry.registerDefaults();
            getLogger().info("✓ PlotTemplateRegistry initialized");

            plotSkinService = new PlotSkinService(this, plotManager.getStorage());
            plotSkinService.start();
            getLogger().info("✓ PlotSkinService initialized");

            pathGenerator = new PathGenerator(this, plotManager);
            getLogger().info("✓ PathGenerator initialized");

            clanShaftService = new de.streuland.clan.ClanShaftService(this, plotManager);
            getLogger().info("✓ ClanShaftService initialized");

            clanManager = new ClanManager(this, plotManager, pathGenerator, clanShaftService);
            clanStorage = new ClanStorage(this);
            clanManager.loadAll(clanStorage.loadAll());
            getLogger().info("✓ ClanManager initialized (" + clanManager.getTotalClanCount() + " clans loaded)");
            clanManager.rebuildClanStructures();
            getServer().getPluginManager().registerEvents(new de.streuland.clan.ClanShaftListener(clanShaftService, clanManager), this);
            getServer().getPluginManager().registerEvents(new de.streuland.clan.ClanWarListener(clanManager), this);
            getServer().getPluginManager().registerEvents(new de.streuland.clan.ClanStorageChestListener(clanShaftService, clanManager), this);
            getServer().getScheduler().runTaskTimer(this, () -> {
                if (clanManager != null) {
                    clanManager.update();
                }
            }, 20L * 60 * 60, 20L * 60 * 60 * 24L);

            discordNotifier = new DiscordNotifier(this);
            if (features.approvalsEnabled()) {
                plotApprovalService = new PlotApprovalService(this, plotManager, pathGenerator, discordNotifier);
                getLogger().info("✓ PlotApprovalService initialized");
            }

            SnapshotStorage snapshotStorage = new SnapshotStorage(this);
            snapshotManager = new SnapshotManager(this, plotManager, snapshotStorage);
            snapshotService = new SnapshotService(this, plotManager, snapshotManager);
            plotBackupCoordinator = new PlotBackupCoordinator(plotManager, snapshotService);
            plotAuditLogService = new PlotAuditLogService(5000);

            ruleEngine = new RuleEngine(plotManager, new DefaultPlotLevelProvider());
            ruleEngine.registerProvider(new ExampleRules());

            biomeBonusService = new BiomeBonusService(plotManager, getConfig());
            ruleEngine.setBiomeBonusService(biomeBonusService);
            analyticsService = new InMemoryPlotAnalyticsService();

            if (features.biomesEnabled()) {
                biomeEffectScheduler = new BiomeEffectScheduler(this, plotManager, biomeBonusService);
                biomeEffectScheduler.start();
                getLogger().info("✓ BiomeEffectScheduler initialized");
            }

            seasonalWeatherService = new SeasonalWeatherService(this, analyticsService);
            seasonalWeatherService.start();
            getLogger().info("✓ SeasonalWeatherService initialized");

            particleEffectScheduler = new ParticleEffectScheduler(this, seasonalWeatherService);
            particleEffectScheduler.start();
            getLogger().info("✓ ParticleEffectScheduler initialized");

            seasonalEffectListener = new SeasonalEffectListener(seasonalWeatherService, analyticsService);
            getServer().getPluginManager().registerEvents(seasonalEffectListener, this);

            transactionManager = new TransactionManager(this);
            getLogger().info("✓ TransactionManager initialized");

            blockChangeLogger = new BlockChangeLogger(this, plotManager);
            plotChangeJournal = new PlotChangeJournal(this, plotManager);
            journalManager = new JournalManager(this, plotChangeJournal);
            adminPlotService = new AdminPlotService(plotManager, snapshotManager, blockChangeLogger);

            plotFlagManager = new PlotFlagManager(plotManager);
            worldGuardCompat = new WorldGuardCompat(this, plotManager, plotFlagManager);
            plotFlagManager.registerHook(worldGuardCompat);
            worldGuardCompat.syncAllPlots();

            protectionListener = new ProtectionListener(this, plotManager, plotFlagManager, messageProvider);
            blockChangeListener = new BlockChangeListener(this, plotManager, blockChangeLogger, analyticsService,
                    plotChangeJournal, journalManager);
            ruleListener = new RuleListener(this, ruleEngine, biomeBonusService);
            getLogger().info("✓ Protection/BlockChange/Rule listeners registered");

            setupEconomy();
            plotEconomyHook = new de.streuland.economy.PlotEconomyHook(this);
            clanManager.setEconomyHook(plotEconomyHook);
            if (economy == null) {
                getLogger().warning("Vault Economy provider not found. Plot market will be disabled.");
            } else {
                getLogger().info("✓ Vault economy connected: " + economy.getName());
            }

            movementPassService = new de.streuland.movement.MovementPassService(this, plotEconomyHook);
            marketStandService = new de.streuland.marketstand.MarketStandService(this, plotEconomyHook);
            movementGuard = new de.streuland.movement.MovementGuard(plotManager, clanManager, movementPassService, marketStandService);
            getServer().getPluginManager().registerEvents(movementGuard, this);
            getLogger().info("✓ MovementGuard initialized");
            getServer().getPluginManager().registerEvents(new de.streuland.marketstand.MarketStandListener(marketStandService), this);
            getLogger().info("✓ MarketStandService initialized");

            questService = new QuestService(this, plotManager.getStorage(), ruleEngine);
            getLogger().info("✓ QuestService initialized");

            neighborhoodService = new NeighborhoodService(this, plotManager, new DistrictClusterService(), analyticsService);
            resourceSyncScheduler = new ResourceSyncScheduler(this, neighborhoodService);
            resourceSyncScheduler.start();
            getLogger().info("✓ Neighborhood system initialized");

            districtManager = new DistrictManager(this, plotManager);
            plotManager.setDistrictManager(districtManager);
            districtProgressService = new DistrictProgressService(this, plotManager, districtManager);
            getServer().getPluginManager().registerEvents(districtManager, this);
            getServer().getPluginManager().registerEvents(districtProgressService, this);
            traderNpcService = new TraderNpcService(this, plotManager, districtManager, analyticsService, economy);
            traderNpcService.start();
            getServer().getPluginManager().registerEvents(traderNpcService, this);
            questTracker = new QuestTracker(plotManager, districtManager, questService);
            getServer().getPluginManager().registerEvents(questTracker, this);
            getLogger().info("✓ District system initialized");

            pricingEngine = new PricingEngine(this, plotManager, neighborhoodService);
            plotPriceCommand = new PlotPriceCommand(pricingEngine);
            plotMarketService = new PlotMarketService(this, plotManager, districtManager, analyticsService, economy, discordNotifier);

            saveResource("plot-upgrades.yml", false);
            PlotUpgradeTree upgradeTree = YamlPlotUpgradeCatalog.load(new File(getDataFolder(), "plot-upgrades.yml"));
            PlotOwnershipResolver ownershipResolver = (plotId, playerId) -> {
                Plot plot = plotManager.getStorage(plotManager.getWorldForPlot(plotId)).getPlot(plotId);
                return plot != null && playerId != null && playerId.equals(plot.getOwner());
            };
            plotUpgradeService = new DefaultPlotUpgradeService(
                    upgradeTree,
                    new PlotStorageBackedUpgradeStorage(plotManager.getStorage()),
                    plotEconomyHook,
                    ownershipResolver
            );
            PlotUpgradeCommand plotUpgradeCommand = new PlotUpgradeCommand(plotManager, plotUpgradeService);

            portalManager = new PortalManager(this, plotManager, new de.streuland.warp.PlotEconomyHook(economy), new CooldownManager());
            getServer().getPluginManager().registerEvents(portalManager, this);

            registerCommands(plotUpgradeCommand);

            if (features.backupsEnabled()) {
                dailyPlotBackupService = new DailyPlotBackupService(this, snapshotService);
                dailyPlotBackupService.start();
            }

            if (features.dashboardApiEnabled()) {
                DashboardDataExporter exporter = new DashboardDataExporter(plotManager.getStorage());
                de.streuland.invite.SqliteInvitationGateway inviteGateway = new de.streuland.invite.SqliteInvitationGateway(getDataFolder().toPath().resolve("invite.sqlite"));
                restApiController = new RestApiController(
                        this,
                        plotManager,
                        neighborhoodService,
                        analyticsService,
                        exporter,
                        plotMarketService,
                        plotApprovalService,
                        districtManager,
                        plotBackupCoordinator,
                        plotAuditLogService,
                        inviteGateway
                );
                restApiController.start();
                getLogger().info("✓ Dashboard API initialized");
            }

            if (getConfig().getBoolean("web.enabled", false)) {
                WebServer.PlotGatewayAdapter gateway = new WebServer.PlotGatewayAdapter(plotManager);
                de.streuland.invite.SqliteInvitationGateway invitationGateway = new de.streuland.invite.SqliteInvitationGateway(getDataFolder().toPath().resolve("invite.sqlite"));
                de.streuland.auth.SqliteUserGateway userGateway = new de.streuland.auth.SqliteUserGateway(getDataFolder().toPath().resolve("invite.sqlite"));
                AdminObservabilityService observabilityService = new AdminObservabilityService(gateway, analyticsService);
                webServer = new WebServer("0.0.0.0", getConfig().getInt("web.port", 8090),
                        getConfig().getString("web.token", ""), gateway, observabilityService, invitationGateway, userGateway, getLogger());
                webServer.start();
                getLogger().info("✓ Admin web server listening on http://0.0.0.0:" + getConfig().getInt("web.port", 8090));
            }

            getLogger().info("===============================================");
            getLogger().info("Streuland enabled successfully!");
            getLogger().info("Loaded " + plotManager.getAllPlots().size() + " plots");
            getLogger().info("===============================================");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Streuland: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands(PlotUpgradeCommand upgradeCommand) {
        PluginCommand plotCommand = getCommand("plot");
        if (plotCommand == null) {
            throw new IllegalStateException("Command 'plot' is not defined in plugin.yml");
        }

        PlotCommandExecutor commandExecutor = new PlotCommandExecutor(
                this,
                plotManager,
                pathGenerator,
                snapshotManager,
                ruleEngine,
                plotSkinService,
                biomeBonusService,
                neighborhoodService,
                questService,
                questTracker,
                plotMarketService,
                adminPlotService,
                analyticsService,
                traderNpcService,
                seasonalWeatherService,
                plotFlagManager,
                upgradeCommand
        );

        plotCommand.setExecutor(commandExecutor);
        plotCommand.setTabCompleter(commandExecutor);

        if (plotApprovalService != null && getCommand("plotapprove") != null) {
            getCommand("plotapprove").setExecutor(new PlotApprovalCommand(plotApprovalService));
        }
        if (getCommand("district") != null) {
            DistrictCommandExecutor districtCommandExecutor = new DistrictCommandExecutor(plotManager, districtManager, messageProvider);
            getCommand("district").setExecutor(districtCommandExecutor);
            getCommand("district").setTabCompleter(districtCommandExecutor);
        }
        if (getCommand("streuland") != null) {
            getCommand("streuland").setExecutor(new StreulandCommandExecutor(
                    plotManager,
                    new StreulandDiagnosticsService(plotManager, getLogger()),
                    plotBackupCoordinator,
                    plotAuditLogService
            ));
        }
        if (getCommand("clan") != null) {
            getCommand("clan").setExecutor(new de.streuland.clan.ClanCommand(clanManager));
        }
        if (getCommand("markt") != null) {
            getCommand("markt").setExecutor(new de.streuland.marketstand.MarketStandCommand(marketStandService));
        }

        getLogger().info("Optional modules active: backups=" + features.backupsEnabled()
                + ", dashboard/api=" + features.dashboardApiEnabled()
                + ", upgrades=" + features.upgradesEnabled());
    }

    private void validateStartupConfiguration() {
        if (getConfig().getConfigurationSection("worlds") == null && getConfig().getString("world.name") == null) {
            throw new IllegalStateException("Missing world configuration. Expected 'world.name' or configured worlds section.");
        }
        if (getCommand("plot") == null) {
            throw new IllegalStateException("Command 'plot' is not defined in plugin.yml");
        }
        if (getCommand("district") == null) {
            getLogger().warning("Command 'district' is missing in plugin.yml; district command will not be available.");
        }
        new ConfigValidationService(this).validateAndNormalize();
        if (getConfig().getBoolean("web.enabled", false)) {
            String token = getConfig().getString("web.token", "");
            if (token == null || token.isBlank()) {
                getLogger().warning("web.enabled is true but web.token is empty. Web API will still start but is insecure.");
            }
        }
    }

    private void initializeStorageAdapter() {
        String type = getConfig().getString("storage.type", "yaml").toLowerCase();
        Path dataPath = getDataFolder().toPath();
        if ("sqlite".equals(type)) {
            Path sqlitePath = dataPath.resolve(getConfig().getString("storage.sqlite-file", "db.sqlite"));
            SqlitePlotStorage sqliteStorage = new SqlitePlotStorage(sqlitePath);
            if (getConfig().getBoolean("storage.migrate-yaml-on-startup", false)) {
                int migrated = sqliteStorage.migrateFromYaml(dataPath.resolve(getConfig().getString("storage.data-folder", "plots")));
                getLogger().info("SQLite migration completed. Migrated plots: " + migrated);
            }
            configuredStorageAdapter = sqliteStorage;
            return;
        }
        configuredStorageAdapter = new YamlPlotStorage(dataPath.resolve(getConfig().getString("storage.data-folder", "plots")));
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
    }

    @Override
    public void onDisable() {
        if (clanStorage != null) {
            clanStorage.saveToDisk();
        }
        if (plotSkinService != null) plotSkinService.stop();
        if (biomeEffectScheduler != null) biomeEffectScheduler.stop();
        if (resourceSyncScheduler != null) resourceSyncScheduler.stop();
        if (seasonalWeatherService != null) seasonalWeatherService.stop();
        if (particleEffectScheduler != null) particleEffectScheduler.stop();
        if (restApiController != null) restApiController.stop();
        if (dailyPlotBackupService != null) dailyPlotBackupService.stop();
        if (webServer != null) webServer.stop();
    }

    public PlotManager getPlotManager() {
        return plotManager;
    }

    public PlotStorage getPlotStorage() {
        return plotManager.getStorage();
    }

    public PathGenerator getPathGenerator() {
        return pathGenerator;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public PlotApprovalService getPlotApprovalService() {
        return plotApprovalService;
    }

    public PlotTemplateRegistry getPlotTemplateRegistry() {
        return plotTemplateRegistry;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }

    public MessageProvider getMessageProvider() {
        return messageProvider;
    }

    public Economy getEconomy() {
        return economy;
    }

    public de.streuland.economy.PlotEconomyHook getPlotEconomyHook() {
        return plotEconomyHook;
    }

    public de.streuland.movement.MovementPassService getMovementPassService() {
        return movementPassService;
    }

    public FeatureToggles getFeatures() {
        return features;
    }
}
