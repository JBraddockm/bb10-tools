package com.qnx.bbt.packager;

import com.qnx.bbt.airpackager.BarAirPackager;
import com.qnx.bbt.bar.BARManifestParser;
import com.qnx.bbt.bar.BARPackager;
import com.qnx.bbt.bar.FileUtils;
import com.qnx.bbt.custompackager.BarCustomPackager;
import com.qnx.bbt.deploy.Deploy;
import com.qnx.bbt.nativepackager.BarNativePackager;
import com.qnx.bbt.packager.Option.OptionVisibity;
import com.qnx.bbt.pythonpackager.BarPythonPackager;
import com.qnx.bbt.uripackager.BarUriPackager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.rim.device.codesigning.barsigner.BarSigner;
import net.rim.device.codesigning.debugtoken.DebugTokenRequest;
import net.rim.tools.bar.BarPackager;
import org.xml.sax.SAXException;

public abstract class AbstractPackager implements IPackagerOutput {
  protected static final String PACKAGE = "package";
  protected static final String PACKAGE_MANIFEST = "packageManifest";
  protected static final String SIGNDEV = "signdev";
  protected static final String SIGNRIM = "signrim";
  protected static final String INSTALL = "install";
  protected static final String LAUNCH = "launch";
  protected static final String LIST_MANIFEST = "listManifest";
  protected static final String TARGET_FILE_MAP = "targetFileMap";
  protected static final String TOKEN = "token";
  protected static final String GENXML = "genxml";
  private static final String OPTION_E = "-e";
  private static final String OPTION_C = "-C";
  protected static String bbtsdk;
  protected static String airsdk;
  private final ArrayList<String> arguments = new ArrayList();
  protected HashSet<String> pcommands = new HashSet();
  protected String host;
  protected String target;
  protected String password;
  protected String user;
  protected String keyStore;
  protected String barFile;
  protected ArrayList<String> sourceFiles;
  protected String storePass;
  protected String alias;
  protected String cskPass;
  protected String publisher;
  protected boolean devMode = false;
  protected String device;
  protected File tempManifest;
  protected boolean airvalidation = true;
  protected boolean barvalidation = true;
  protected boolean list;
  protected LinkedHashMap<String, String> env = new LinkedHashMap();
  protected LinkedHashMap<String, String> pathVars = new LinkedHashMap();
  protected String arch;
  protected String platformVersion;
  protected String runtimeWrapper;
  protected String configurationId;
  protected CommonOptions options;
  private IPackagerOutput output = new DefaultPackagerOutput();
  private IBarManifestValueProvider provider;
  private String barVersion;
  private String debugToken;
  private boolean renewDebugToken;
  private boolean installDebugToken;
  private boolean createDebugToken;
  private List<String> devicePins;
  private Map<String, SourceFile> targetToSources;

  protected AbstractPackager() {}

  protected static boolean explicitPackager(String[] args) {
    CommonOptions options = new CommonOptions(new DefaultPackagerOutput());
    ArrayList<String> cmdArgs = new ArrayList();
    cmdArgs.addAll(Arrays.asList(args));
    options.parseOptions(cmdArgs);
    if (options.APPTYPE.isSet()) {
      String apptype = options.APPTYPE.getValue();
      if (apptype.equals("native")) {
        (new BarNativePackager()).runPackager(args);
      } else if (apptype.equals("air")) {
        (new BarAirPackager()).runPackager(args);
      } else if (apptype.equals("uri")) {
        (new BarUriPackager()).runPackager(args);
      } else if (apptype.equals("custom")) {
        (new BarCustomPackager()).runPackager(args);
      } else {
        if (!apptype.equals("python")) {
          throw new IllegalArgumentException(
              Messages.getString("AbstractPackager.UnknownAppType", new Object[] {apptype}));
        }

        (new BarPythonPackager()).runPackager(args);
      }

      return true;
    } else {
      return false;
    }
  }

  public static File getDefaultKeyStore() {
    String userHome = System.getProperty("user.home");
    return new File(userHome, ".keystore");
  }

  public static File getDefaultDebugToken() {
    String userHome = System.getProperty("user.home");
    return new File(userHome, "debugToken.bar");
  }

  public static File getLocalStorageDir() {
    String osName = System.getProperty("os.name");
    String fileSep = System.getProperty("file.separator");
    String userHome = System.getProperty("user.home");
    String rimDir = "Research In Motion";
    String appDirName = "";
    if (osName.startsWith("Mac OS X")) {
      appDirName = userHome + fileSep + "Library" + fileSep + rimDir;
    } else if (osName.startsWith("Linux")) {
      appDirName = userHome + fileSep + ".rim";
    } else if (!osName.contains("Vista") && !osName.contains("Windows 7")) {
      appDirName =
          userHome + fileSep + "Local Settings" + fileSep + "Application Data" + fileSep + rimDir;
    } else {
      appDirName = userHome + fileSep + "AppData" + fileSep + "Local" + fileSep + rimDir;
    }

    File appDir = new File(appDirName);
    if (!appDir.exists()) {
      appDir.mkdir();
    }

    if (!appDir.exists()) {
      appDir = new File(userHome + fileSep + rimDir);
      appDir.mkdir();
    }

    if (!appDir.isDirectory()) {
      throw new IllegalStateException(
          Messages.getString("Pack.Cannot_Create_Application_Data_Directory") + appDir);
    } else {
      return appDir;
    }
  }

  private static void interceptSystemExit() {
    DefaultPackagerOutput.interceptSystemExit();
  }

  protected static void readWriteStream(InputStream readStream, OutputStream writeStream)
      throws IOException {
    byte[] buffer = new byte[4096];

    for (int bytesRead = readStream.read(buffer);
        bytesRead > 0;
        bytesRead = readStream.read(buffer)) {
      writeStream.write(buffer, 0, bytesRead);
    }
  }

  public static File getAirSdkFolder() {
    return airsdk != null ? new File(airsdk) : null;
  }

  public static File getSdkFolder() {
    if (bbtsdk != null) {
      return new File(bbtsdk);
    } else {
      try {
        CodeSource codeSource = AbstractPackager.class.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
          String path = URLDecoder.decode(codeSource.getLocation().getPath(), "UTF-8");
          File barAirPackagerJarFile = new File(path);
          if (barAirPackagerJarFile.getParentFile() != null) {
            return barAirPackagerJarFile.getParentFile().getParentFile();
          }
        }
      } catch (Exception var3) {
      }

      return null;
    }
  }

  public static String generatePackageId(String packageName, boolean devMode) {
    String mode = devMode ? "Dev" : "Rel";
    String rem = Integer.toHexString(packageName.hashCode());
    String name = packageName.replaceAll("[^\\w]", "_");
    String res = "test" + mode + "_" + lastNchars(name, 11) + lastNchars(rem, 8);
    if (res.length() != 27) {
      throw new IllegalArgumentException("Invalid package id " + res);
    } else {
      return res;
    }
  }

  private static String lastNchars(String str, int n) {
    String res = str;
    int len = str.length();
    if (len == n) {
      res = str;
    } else if (len < n) {
      while (res.length() < n) {
        res = res + "_";
      }
    } else {
      res = str.substring(len - n);
    }

    return res;
  }

  protected IPackagerOutput getOutput() {
    return this.output;
  }

  public void setOutput(IPackagerOutput output) {
    this.output = output;
  }

  public void setVerbose(boolean verbose) {
    this.output.setVerbose(verbose);
  }

  public void setTrace(boolean trace) {
    this.output.setTrace(trace);
  }

  protected void runPackager(String[] args) {
    try {
      this.doRun(args);
    } catch (IllegalArgumentException e) {
      this.printFatalError(e);
    } catch (IOException e) {
      this.printFatalError(e);
    } catch (Throwable e) {
      e.printStackTrace();
      this.printFatalError(e);
    }
  }

  protected void runPackager(String[] args, IPackagerOutput output)
      throws IOException, GeneralSecurityException, SAXException, Exception {
    this.output = output;
    this.doRun(args);
  }

  public void doRun(String[] args)
      throws IOException, GeneralSecurityException, SAXException, Exception {
    this.parseCommandLineOptions(args);
    this.initializeProvider();
    this.tempManifest = File.createTempFile("bar", ".MF");
    this.tempManifest.deleteOnExit();
    this.callTemplateGenerator();
    this.parseDescriptorAndCreateBarManifest();
    this.callBarPackagerValidation();
    this.callSigner();
    this.callInfo();
    this.callDebugTokenProcessor();
    this.callInstaller();
    this.callLauncher();
    this.cleanUp();
  }

  private void callTemplateGenerator() throws Exception {
    if (this.pcommands.contains("genxml")) {
      String file = this.options.COMMAND_GENERATE_XML.getValue();
      PrintStream stream = null;
      boolean out = file.equals("-");

      try {
        if (out) {
          stream = this.output.getOutputStream();
        } else {
          try {
            stream = new PrintStream(new FileOutputStream(file), true, "UTF-8");
          } catch (UnsupportedEncodingException var9) {
            stream = new PrintStream(new FileOutputStream(file));
          }
        }

        try {
          this.generateXmlTemplate(stream);
          this.printSuccess(
              Messages.getString("AbstractPackager.XmlGenerate", new Object[] {file}));
        } catch (Exception e) {
          if (!out) {
            (new File(file)).delete();
          }

          throw e;
        }
      } finally {
        if (!out && stream != null) {
          stream.close();
        }
      }
    }
  }

  protected void generateXmlTemplate(PrintStream stream) throws IOException {
    InputStream resourceAsStream = this.getClass().getResourceAsStream("bar-descriptor.xml");
    if (resourceAsStream == null) {
      throw new IllegalArgumentException("This packager does not support this functionality");
    } else {
      FileUtils.readWriteStream(resourceAsStream, stream);
    }
  }

  protected abstract void initializeProvider();

  protected void callInfo() throws IOException {
    if (this.pcommands.contains("listManifest")) {
      String manifest = this.readManifest(this.barFile);
      manifest = manifest.replaceAll("\r", "");
      String[] strings = manifest.split("\n");

      for (String line : strings) {
        this.output.getOutputStream().println(line);
      }
    }

    if (this.pcommands.contains("targetFileMap")) {
      for (Map.Entry<String, SourceFile> entry : this.targetToSources.entrySet()) {
        this.output
            .getOutputStream()
            .println(
                "target_map:"
                    + this.provider.getAssetsFolder(((SourceFile) entry.getValue()).isPublic())
                    + (String) entry.getKey()
                    + '='
                    + ((SourceFile) entry.getValue()).getFile());
      }
    }
  }

  protected String readManifest(String file) throws IOException {
    String entryName = "META-INF/MANIFEST.MF";
    String manifest = FileUtils.readZipTextEntry(new File(file), entryName, "UTF-8");
    return manifest;
  }

  public void printSuccess(String msg) {
    this.output.printSuccess(msg);
  }

  public void printTrace(String msg) {
    this.output.printTrace(msg);
  }

  protected String makeRelative(String file, String dir) {
    if (!dir.endsWith(File.separator)) {
      dir = dir + File.separator;
    }

    if (file.startsWith(dir)) {
      file = file.substring(dir.length());
    }

    return file;
  }

  protected void callLauncher() {
    if (this.pcommands.contains("launch")) {
      ArrayList<String> commands = new ArrayList();
      commands.add("-launchApp");
      this.addDeploymentOptions(commands);
      this.doDeploy(commands);
    }
  }

  protected void doDeploy(ArrayList<String> commands) {
    this.printTrace("blackberry-deploy " + commands);
    Deploy.main((String[]) commands.toArray(new String[commands.size()]));
  }

  protected void callInstaller() {
    if (this.pcommands.contains("install")) {
      ArrayList<String> commands = new ArrayList();
      commands.add("-installApp");
      this.addDeploymentOptions(commands);
      this.doDeploy(commands);
    }
  }

  protected void callDebugTokenProcessor() throws Exception {
    if (this.pcommands.contains("token")) {
      if (this.debugToken == null) {
        this.debugToken = this.barFile;
      }

      if (this.createDebugToken) {
        if (this.debugToken == null) {
          this.debugToken = getDefaultDebugToken().getAbsolutePath();
          this.printWarning(
              MessageFormat.format(
                  Messages.getString("AbstractPackager.DefaultDebugTokenUsed"), this.debugToken));
        }

        if ((new File(this.debugToken)).exists()) {
          String option = "-renewDebugToken";
          throw new IllegalArgumentException(
              MessageFormat.format(
                  Messages.getString("AbstractPackager.DebugTokenAlreadyExists"),
                  option,
                  this.debugToken));
        }

        this.renewDebugToken = false;
        this.collectDevicePinFromDevice();
        this.generateDebugToken();
      }

      if (this.renewDebugToken) {
        this.collectDevicePinFromDebugToken();
        this.generateDebugToken();
      }

      if (this.installDebugToken) {
        this.installDebugTokenOnDevice();
      }
    }
  }

  protected void collectDevicePinFromDebugToken() throws IOException {
    if (this.debugToken == null) {
      File dt = getDefaultDebugToken();
      if (dt.exists()) {
        this.debugToken = dt.getAbsolutePath();
        this.printWarning(
            MessageFormat.format(
                Messages.getString("AbstractPackager.DefaultDebugTokenUsed"), this.debugToken));
      }
    }

    if (this.debugToken == null) {
      throw new IllegalArgumentException(Messages.getString("Pack.Bar_Package_Is_Required"));
    } else {
      String manifest = this.readManifest(this.debugToken);
      BARManifestParser.BarManifestAttributes attrs = BARManifestParser.parseManifestText(manifest);
      this.devicePins = attrs.getAttributeMultiEntry("Debug-Token-Device-Id");
    }
  }

  protected void installDebugTokenOnDevice() {
    if (this.debugToken == null) {
      throw new IllegalArgumentException(Messages.getString("Pack.Bar_Package_Is_Required"));
    } else if (this.device == null) {
      throw new IllegalArgumentException(Messages.getString("Pack.Device_Address_Is_Required"));
    } else {
      ArrayList<String> commands = new ArrayList();
      commands.add("-installDebugToken");
      commands.add(this.debugToken);
      this.addConnectionOptions(commands);
      this.doDeploy(commands);
    }
  }

  private void collectDevicePinFromDevice() {
    if (this.device == null) {
      throw new IllegalArgumentException(Messages.getString("Pack.Device_Address_Is_Required"));
    } else {
      ArrayList<String> commands = new ArrayList();
      commands.add("-listDeviceInfo");
      this.addConnectionOptions(commands);
      PrintStream restore = System.out;
      ByteArrayOutputStream ba = new ByteArrayOutputStream();
      String pin = null;

      try {
        System.setOut(new PrintStream(ba));
        this.doDeploy(commands);
        Pattern p = Pattern.compile("devicepin::0x([0-9A-Fa-f]+)");
        Matcher matcher = p.matcher(ba.toString());
        if (matcher.find()) {
          pin = matcher.group(1);
        }
      } finally {
        System.setOut(restore);
      }

      if (pin == null) {
        throw new IllegalArgumentException(Messages.getString("AbstractPackager.CannotGetPin"));
      } else {
        this.devicePins = new ArrayList();
        this.devicePins.add(String.valueOf(Long.parseLong(pin, 16)));
      }
    }
  }

  private void generateDebugToken() throws Exception {
    if (this.debugToken == null) {
      this.debugToken = getDefaultDebugToken().getAbsolutePath();
      this.printWarning(
          MessageFormat.format(
              Messages.getString("AbstractPackager.DefaultDebugTokenUsed"), this.debugToken));
    }

    ArrayList<String> commands = new ArrayList();

    for (String devicePin : this.devicePins) {
      commands.add("-deviceid");
      commands.add(String.valueOf(devicePin));
    }

    this.addSigningOptions(commands);
    commands.add(this.debugToken);
    this.doRequestDebugToken(commands);
  }

  protected void doRequestDebugToken(ArrayList<String> commands) throws Exception {
    this.printTrace("blackberry-debugtokenrequest " + commands);
    DebugTokenRequest.main((String[]) commands.toArray(new String[commands.size()]));
  }

  protected void addDeploymentOptions(ArrayList<String> commands) {
    if (this.barFile == null) {
      throw new IllegalArgumentException(Messages.getString("Pack.Bar_Package_Is_Required"));
    } else if (this.device == null) {
      throw new IllegalArgumentException(Messages.getString("Pack.Device_Address_Is_Required"));
    } else {
      commands.add("-package");
      commands.add(this.barFile);
      if (this.devMode) {
        commands.add("-devMode");
      }

      this.addConnectionOptions(commands);
    }
  }

  protected void addConnectionOptions(ArrayList<String> commands) {
    commands.add("-device");
    commands.add(this.device);
    if (this.user != null) {
      commands.add("-user");
      commands.add(this.user);
    }

    if (this.password != null) {
      commands.add("-password");
      commands.add(this.password);
    }

    if (this.host != null) {
      commands.add("-debugHost");
      commands.add(this.host);
    }
  }

  protected void callSigner() throws Exception {
    if (this.pcommands.contains("signrim")) {
      ArrayList<String> commands = new ArrayList();
      this.addSigningOptions(commands);
      commands.add(this.barFile);
      commands.add("RDK");
      this.printTrace("blackberry-signer " + commands);
      BarSigner.main((String[]) commands.toArray(new String[commands.size()]));
      this.printSuccess(Messages.getString("Pack.Signing_Successful_RDK"));
    }

    if (this.pcommands.contains("signdev")) {
      ArrayList<String> commands = new ArrayList();
      this.addSigningOptions(commands);
      commands.add(this.barFile);
      if (this.alias == null) {
        this.alias = "author";
      }

      commands.add(this.alias);
      BarSigner.main((String[]) commands.toArray(new String[commands.size()]));
      this.printSuccess(
          Messages.getString("Pack.Signing_Successful_Author", new Object[] {this.alias}));
    }
  }

  protected void addSigningOptions(ArrayList<String> commands) {
    if (this.keyStore != null) {
      commands.add("-keystore");
      commands.add(this.keyStore);
    }

    if (this.storePass != null) {
      commands.add("-storepass");
      commands.add(this.storePass);
    }

    if (this.cskPass != null) {
      commands.add("-cskpass");
      commands.add(this.cskPass);
    } else if (this.storePass != null) {
      commands.add("-cskpass");
      commands.add(this.storePass);
    }
  }

  protected void callBarPackagerValidation() {
    if (this.pcommands.contains("package") && this.barvalidation) {
      this.validateManifest(this.tempManifest);
    }
  }

  private void validateManifest(File manifest) {
    PrintStream oldOut = System.out;

    try {
      if (this.tempManifest.exists()) {
        ArrayList<String> commands = new ArrayList();
        commands.add("-verifymanifest");
        commands.add(this.tempManifest.getPath());
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));
        BarPackager.main((String[]) commands.toArray(new String[commands.size()]));
      }
    } catch (UnsupportedClassVersionError var9) {
      this.printWarning(Messages.getString("Pack.Bar_Validation_Is_Skipped"));
    } catch (SecurityException e) {
      if (!e.getMessage().equals("success")) {
        throw e;
      }
    } catch (Exception var11) {
      this.printWarning(Messages.getString("Pack.BarValidationSkippedInternalErrror"));
    } finally {
      this.tempManifest.deleteOnExit();
      System.setOut(oldOut);
    }
  }

  private void parseDescriptorAndCreateBarManifest()
      throws IOException, GeneralSecurityException, SAXException {
    if (this.pcommands.contains("package")
        || this.pcommands.contains("packageManifest")
        || this.pcommands.contains("targetFileMap")) {
      this.provider.setConfigurationId(this.configurationId);
      this.provider.setDebug(this.isDebug());
      this.provider.setRuntimeWrapper(this.runtimeWrapper);
      BARPackager barPackager = new BARPackager(this, this.provider, this.barVersion);

      try {
        this.initializeBARPackager(barPackager);
        barPackager.setOutput(new File(this.barFile));
        barPackager.addPathVariables(this.pathVars);
        this.parseSourceFiles(this.sourceFiles, barPackager);
        barPackager.associateSourceAssets();
        barPackager.addPathVariables(this.provider.getPathVars());
        this.addProviderSourceFiles(barPackager);
        barPackager.getBARStream().setManifestFile(this.tempManifest);
        barPackager.getBARStream().setDevMode(this.devMode);
        barPackager.getBARStream().setConfigType(this.target);
        barPackager.setDebugHost(this.host);
        barPackager.getBARStream().setPackageAuthor(this.publisher);
        String buildId = this.options.BUILD_ID_FILE.getValue();
        if (buildId == null) {
          buildId = this.options.BUILD_ID.getValue();
        }

        barPackager.getBARStream().setBuildId(buildId);
        barPackager.getBARStream().setAppVersion(this.options.APP_VERSION.getValue());
        barPackager.getBARStream().setDigestGen(true);
        barPackager.getBARStream().setListFiles(this.list);
        barPackager.getBARStream().setEnv(this.env);
        barPackager.getBARStream().setArchietcture(this.arch);
        barPackager.getBARStream().setPlatformVersion(this.platformVersion);
        barPackager.getBARStream().setDebugToken(this.debugToken);
        barPackager.getBARStream().setPackageId(this.options.PACKAGE_ID.getValue());
        this.finalizeBARPackager(barPackager);
        if (this.pcommands.contains("package")) {
          barPackager.createPackage();
          this.printSuccess(Messages.getString("Pack.Package_Created") + this.barFile);
        } else if (this.pcommands.contains("packageManifest")) {
          barPackager.createPackageManifest();
          this.printSuccess(Messages.getString("Pack.Package_Created") + this.barFile);
        }

        this.targetToSources = barPackager.getSources();
      } finally {
        barPackager.close();
      }
    }
  }

  private boolean isDebug() {
    return this.target != null && this.target.contains("debug");
  }

  protected void initializeBARPackager(BARPackager barPackager) throws IOException {}

  protected void finalizeBARPackager(BARPackager barPackager) throws IOException {}

  private List<String> parseCommandLineOptions(String[] argsOrig) {
    this.options = this.createOptions();
    ArrayList<String> remArgs = new ArrayList();
    this.createHelpHandlers();
    this.readEnvToPathVars();
    this.createFilesHandler(remArgs);
    ArrayList<String> cmdArgs = new ArrayList();
    cmdArgs.addAll(Arrays.asList(argsOrig));
    this.options.parseOptions(cmdArgs);
    if (this.options.COMMAND_PACKAGE.isSet()) {
      this.pcommands.add("package");
    }

    if (this.options.COMMAND_SIGN.isSet()) {
      this.pcommands.add("signrim");
      this.pcommands.add("signdev");
    }

    if (this.options.COMMAND_SIGNRIM.isSet()) {
      this.pcommands.add("signrim");
    }

    if (this.options.COMMAND_SIGNDEV.isSet()) {
      this.pcommands.add("signdev");
    }

    if (this.options.COMMAND_INSTALL.isSet()) {
      this.pcommands.add("install");
    }

    if (this.options.COMMAND_LAUNCH.isSet()) {
      this.pcommands.add("launch");
    }

    if (this.options.COMMAND_GENERATE_DT.isSet()) {
      this.pcommands.add("token");
      this.createDebugToken = true;
    }

    if (this.options.COMMAND_RENEW_DT.isSet()) {
      this.pcommands.add("token");
      this.renewDebugToken = true;
    }

    if (this.options.COMMAND_INSTALL_DT.isSet()) {
      this.pcommands.add("token");
      this.installDebugToken = true;
    }

    if (this.options.COMMAND_LIST_ASSETS.isSet()) {
      this.list = true;
    }

    if (this.options.COMMAND_GENERATE_XML.isSet()) {
      this.pcommands.add("genxml");
    }

    if (this.options.COMMAND_LIST_MANIFEST.isSet()) {
      this.pcommands.add("listManifest");
    }

    if (this.options.COMMAND_TARGET_FILE_MAP.isSet()) {
      this.pcommands.add("targetFileMap");
    }

    if (this.options.COMMAND_PACKAGE_MANIFEST.isSet()) {
      this.pcommands.add("packageManifest");
    }

    this.target = this.options.TARGET_FORMAT.getValue();
    this.host = this.options.DEBUG_HOST.getValue();
    this.device = this.options.DEVICE.getValue();
    this.keyStore = this.options.KEYSTORE.getValue();
    this.storePass = this.options.STOREPASS.getValue();
    this.cskPass = this.options.CSKPASS.getValue();
    this.alias = this.options.ALIAS.getValue();
    bbtsdk = this.options.PLATFORM_SDK.getValue();
    this.publisher = this.options.AUTHOR.getValue();
    this.devMode = this.options.DEV_MODE.isSet();
    this.debugToken = this.options.DEBUG_TOKEN.getValue();
    this.user = this.options.USER.getValue();
    this.password = this.options.PASSWORD.getValue();
    this.barVersion = this.options.BAR_VERSION.getValue();
    this.barvalidation = !this.options.NO_BARVALIDATION.isSet();
    if (this.options.NO_VALIDATION.isSet()) {
      this.barvalidation = false;
    }

    this.configurationId = this.options.CONFIGURATION.getValue();
    Collection<String> envs = this.options.ENV.getArgs();
    if (envs != null) {
      for (String string : envs) {
        this.addEnvVar(string);
      }
    }

    Collection<String> aargs = this.options.ARG.getArgs();
    if (aargs != null) {
      this.arguments.addAll(aargs);
    }

    if (this.pcommands.size() == 0) {
      this.pcommands.add("package");
    }

    this.sourceFiles = remArgs;
    if (this.sourceFiles.size() >= 1) {
      this.barFile = (String) this.sourceFiles.get(0);
      this.sourceFiles.remove(0);
    } else if (!this.pcommands.contains("token") && !this.pcommands.contains("genxml")) {
      throw new IllegalArgumentException(Messages.getString("Pack.Too_Few_Arguments"));
    }

    this.processSpecialOptions();
    return this.sourceFiles;
  }

  protected void readEnvToPathVars() {
    this.pathVars.putAll(System.getenv());
  }

  public void createHelpHandlers() {
    this.options.NO_ARGS.setHandler(
        new Option.OptionHandler() {
          public void run() {
            AbstractPackager.this.printHelp();
            System.exit(0);
          }
        });
    this.options.HELP.setHandler(
        new Option.OptionHandler() {
          public void run() {
            AbstractPackager.this.printHelp();
            System.exit(0);
          }
        });
    this.options.HELP_A.setHandler(
        new Option.OptionHandler() {
          public void run() {
            AbstractPackager.this.options.printHelp(OptionVisibity.ADVANCED);
            System.exit(0);
          }
        });
    this.options.HELP_H.setHandler(
        new Option.OptionHandler() {
          public void run() {
            AbstractPackager.this.options.printHelp(OptionVisibity.HIDDEN);
            System.exit(0);
          }
        });
    this.options.VERSION.setHandler(
        new Option.OptionHandler() {
          public void run() {
            AbstractPackager.this.printVersion();
            System.exit(0);
          }
        });
    this.options.TRACE.setHandler(
        new Option.OptionHandler() {
          public void run() {
            AbstractPackager.this.output.setTrace(this.opt.isSet());
          }
        });
    this.options.VERBOSE.setHandler(
        new Option.OptionHandler() {
          public void run() {
            AbstractPackager.this.output.setVerbose(this.opt.isSet());
          }
        });
  }

  public void createFilesHandler(final ArrayList<String> remArgs) {
    Option.OptionHandler filesHandler =
        new Option.OptionHandler() {
          public void run() {
            if (this.opt == AbstractPackager.this.options.FILES_C) {
              remArgs.add("-C");
              remArgs.add(this.opt.getValue());
            } else if (this.opt == AbstractPackager.this.options.FILES_E) {
              String file = this.opt.getArg(0);
              String path = this.opt.getArg(1);
              remArgs.add("-e");
              remArgs.add(file);
              remArgs.add(path);
              if (path.startsWith("/")) {
                throw new IllegalArgumentException(
                    Messages.getString("Pack.Path_Cannot_Be_Absolut"));
              }

              if (path.indexOf(92) >= 0) {
                throw new IllegalArgumentException(
                    Messages.getString("Pack.Path_Separator_Must_Be_Slash"));
              }
            } else if (this.opt == AbstractPackager.this.options.BUILD_VAR) {
              AbstractPackager.this.addPathVar(this.opt.getValue());
            } else if (this.opt == AbstractPackager.this.options.FREE_ARG) {
              remArgs.add(this.opt.getValue());
            }
          }
        };
    this.options.FILES_E.setHandler(filesHandler);
    this.options.FILES_C.setHandler(filesHandler);
    this.options.FREE_ARG.setHandler(filesHandler);
    this.options.BUILD_VAR.setHandler(filesHandler);
  }

  protected CommonOptions createOptions() {
    return new CommonOptions(this);
  }

  protected void processSpecialOptions() {
    if (this.barFile != null && !this.barFile.toLowerCase(Locale.ENGLISH).endsWith(".bar")) {
      throw new IllegalArgumentException(
          Messages.getString("Pack.BarFileMustHaveBarExtension") + this.barFile);
    }
  }

  protected void printVersion() {
    this.output
        .getOutputStream()
        .println(this.getToolName() + " " + Messages.getString("Pack.Version") + "1.11");
    this.output.getOutputStream().println("Copyright (C) 2010-2014 BlackBerry Limited");
  }

  protected abstract String getToolName();

  private void addEnvVar(String pair) {
    Pattern varpattern = Pattern.compile("^([a-zA-Z_]\\w*)=(.*)");
    Matcher matcher = varpattern.matcher(pair);
    if (matcher.find()) {
      String var = matcher.group(1);
      String value = matcher.group(2);
      if (value == null) {
        value = "";
      }

      this.env.put(var, value);
    } else {
      throw new IllegalArgumentException(
          Messages.getString("Pack.UnrecognizedVariableAssigment") + pair);
    }
  }

  private void addPathVar(String pair) {
    Pattern varpattern = Pattern.compile("^([a-zA-Z_]\\w*(?::[^\\s]+)?)=(.*)");
    Matcher matcher = varpattern.matcher(pair);
    if (matcher.find()) {
      String var = matcher.group(1);
      String value = matcher.group(2);
      if (value == null) {
        value = "";
      }

      this.pathVars.put(var, value);
    } else {
      throw new IllegalArgumentException(
          Messages.getString("Pack.UnrecognizedVariableAssigment") + pair);
    }
  }

  protected void addProviderSourceFiles(BARPackager barPackager) throws IOException {
    if (barPackager.getSourcesSize() == 0) {
      barPackager.addProviderSourceFiles();
    }
  }

  public void parseSourceFiles(ArrayList<String> sourceFiles, BARPackager barPackager) {
    File cwdOrig = new File(System.getProperty("user.dir"));
    File cwd = this.canonicPath(cwdOrig.getPath());

    for (int j = 0; j < sourceFiles.size(); ++j) {
      String arg = (String) sourceFiles.get(j);
      if (arg.equals("-C")) {
        ++j;
        String dir = (String) sourceFiles.get(j);
        dir = barPackager.substituteVariables(dir);
        cwd = this.canonicPath(dir);
        sourceFiles.set(j, cwd.getPath());
      } else if (arg.equals("-e")) {
        String file = (String) sourceFiles.get(j + 1);
        String path = (String) sourceFiles.get(j + 2);
        path = barPackager.substituteVariables(path);
        file = barPackager.substituteVariables(file);
        File apath = this.canonicPath(file);
        sourceFiles.set(j + 1, apath.getPath());
        if (apath.isDirectory()) {
          this.addSourceFromDirWithPathPrefix(apath, path, barPackager);
        } else {
          barPackager.addSourceWithPath(apath, path, (Asset) null);
        }

        j += 2;
      } else {
        arg = barPackager.substituteVariables(arg);
        File apath = this.canonicPath(arg);
        if (apath.isDirectory()) {
          this.addSourceFromDir(apath, cwd, barPackager);
        } else {
          barPackager.addSourceWithRoot(apath, cwd);
        }

        sourceFiles.set(j, apath.getPath());
      }
    }
  }

  protected void addSourceFromDirWithPathPrefix(File apath, String path, BARPackager barPackager) {
    File[] listFiles = apath.listFiles();
    if (listFiles != null) {
      for (int i = 0; i < listFiles.length; ++i) {
        File file = listFiles[i];
        if (file.isDirectory()) {
          this.addSourceFromDirWithPathPrefix(
              file.getAbsoluteFile(), path + '/' + file.getName(), barPackager);
        } else {
          barPackager.addSourceWithPath(
              file.getAbsoluteFile(), path + '/' + file.getName(), (Asset) null);
        }
      }
    }
  }

  protected void addSourceFromDir(File apath, File cwd, BARPackager barPackager) {
    File[] listFiles = apath.listFiles();
    if (listFiles != null) {
      for (int i = 0; i < listFiles.length; ++i) {
        File file = listFiles[i];
        if (file.isDirectory()) {
          this.addSourceFromDir(file.getAbsoluteFile(), cwd, barPackager);
        } else {
          barPackager.addSourceWithRoot(file.getAbsoluteFile(), cwd);
        }
      }
    }
  }

  protected File canonicPath(String dir) {
    File absoluteFile = (new File(dir)).getAbsoluteFile();
    if (!absoluteFile.exists()) {
      throw new IllegalArgumentException(
          Messages.getString("Pack.FileOrDirDoesNotExist") + absoluteFile);
    } else {
      try {
        return absoluteFile.getCanonicalFile();
      } catch (IOException var4) {
        return absoluteFile;
      }
    }
  }

  private String getArg(String[] args, int i) {
    String arg = this.getArgNoChecks(args, i);
    String value = arg.trim();
    if (value.length() == 0) {
      throw new IllegalArgumentException(
          Messages.getString("Pack.InvalidArgumentFor", new Object[] {args[i]}));
    } else {
      return value;
    }
  }

  protected String getArgNoChecks(String[] args, int i) {
    int j = i + 1;
    if (j >= args.length) {
      throw new IllegalArgumentException(
          Messages.getString("Pack.MissingArgumentFor", new Object[] {args[i]}));
    } else {
      String arg = args[j];
      return arg;
    }
  }

  public void printFatalError(Throwable e) {
    this.output.printFatalError(e);
  }

  public void printWarning(String message) {
    this.output.printWarning(message);
  }

  public void printInfo(String message) {
    this.output.printInfo(message);
  }

  protected abstract void printHelp();

  public void cleanUp() {}

  public ArrayList<String> getArguments() {
    return this.arguments;
  }

  public IBarManifestValueProvider getProvider() {
    if (this.provider == null) {
      throw new IllegalStateException("Provider is not initialized");
    } else {
      return this.provider;
    }
  }

  protected void setProvider(IBarManifestValueProvider p) {
    if (p == null) {
      throw new NullPointerException();
    } else if (this.provider != null) {
      throw new IllegalStateException("Cannot reset provider");
    } else {
      this.provider = p;
    }
  }

  public PrintStream getErrorStream() {
    return this.output.getErrorStream();
  }

  public PrintStream getOutputStream() {
    return this.output.getOutputStream();
  }
}
