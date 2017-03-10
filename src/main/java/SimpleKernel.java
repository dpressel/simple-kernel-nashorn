import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ArrayIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;

import java.io.*;
import org.apache.commons.io.IOUtils;
import java.util.*;
import java.util.stream.Collectors;
import javax.script.*;

public class SimpleKernel
{

    public static int CONTROL = 0;
    public static int STDIN = 1;
    public static int SHELL = 2;

    Socket heartbeatChannel;
    Socket controlChannel;
    Socket stdinChannel;
    Socket shellChannel;
    Socket iopubChannel;

    byte[] key;
    String sessionId;
    Poller items;
    Context context;
    private static final Logger LOG = LoggerFactory.getLogger(SimpleKernel.class);
    boolean exiting;
    private static final String DEFAULT_JAVASCRIPT_ENGINE_ID = "ECMAScript";
    private ScriptEngine scriptEngine;

    public SimpleKernel(Config config)
    {
        key = config.key.getBytes();
        sessionId = UUID.randomUUID().toString();
        //  Prepare our context and sockets
        context = ZMQ.context(1);
        //  Initialize poll set
        items = new Poller(3);
        heartbeatChannel = Utils.createSocket(context, ZMQ.REP, config.ip, config.hb_port);
        controlChannel = Utils.createPolledSocket(context, items, ZMQ.ROUTER, config.ip, config.control_port);
        stdinChannel = Utils.createPolledSocket(context, items, ZMQ.ROUTER, config.ip, config.stdin_port);
        shellChannel = Utils.createPolledSocket(context, items, ZMQ.ROUTER, config.ip, config.shell_port);
        iopubChannel = Utils.createSocket(context, ZMQ.PUB, config.ip, config.iopub_port);
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        scriptEngine = scriptEngineManager.getEngineByName(DEFAULT_JAVASCRIPT_ENGINE_ID);

    }

    void dumpHeader(String where, Message msg)
    {
        LOG.info("=========================================");
        LOG.info("Channel:  " + where);
        LOG.info("=========================================");
        LOG.info("msg_id:   " + msg.header.msg_id);
        LOG.info("msg_type: " + msg.header.msg_type);
        LOG.info("username: " + msg.header.username);
        LOG.info("version:  " + msg.header.version);
        LOG.info("date:     " + msg.header.date);
        LOG.info("-----------------------------------------");
    }

    class HeartbeatThread extends Thread
    {
        public HeartbeatThread()
        {

        }

        @Override
        public void run()
        {
            LOG.info("Starting heartbeat");
            while (!exiting)
            {
                ZMQ.proxy(heartbeatChannel, heartbeatChannel, null);
            }
        }
    }

    protected void loadScriptExtensions(String extensionList) throws IOException
    {
        LOG.info(String.format("Loading plugins from [%s]", extensionList));
        InputStream is = null;
        try
        {
            is = Utils.openResource(extensionList, SimpleKernel.class);
            List<String> lines = IOUtils.readLines(is);
            for (String line : lines)
            {
                String extensionName = line.trim();


                try
                {
                    scriptEngine.eval(new InputStreamReader(Utils.openResource(extensionName, SimpleKernel.class)));
                    LOG.info("Loaded extension " + extensionName);
                }
                catch (Exception ex)
                {
                    LOG.error("Failed to load extension: " + extensionName + ". Attempting to continue", ex);
                }
            }
        }
        catch (IOException e)
        {
            LOG.error("Extension list failed to load", e);
        }
        finally
        {
            if (is != null)
            {
                IOUtils.closeQuietly(is);
            }
        }
    }
    public void start(String extensionListFile) throws Exception
    {
        if (extensionListFile != null)
        {
            loadScriptExtensions(extensionListFile);
        }
        HeartbeatThread thread = new HeartbeatThread();
        thread.start();
        LOG.info("Starting Heartbeat thread");
        exiting = false;
        //  Switch messages between sockets
        while (!Thread.currentThread().isInterrupted() && !exiting)
        {


            items.poll();

            if (items.pollin(CONTROL))
            {
                Message msg = Message.recv(key, controlChannel);
                //dumpHeader("CONTROL", msg);
                if (msg.header.msg_type.equals("shutdown_request"))
                {
                    exiting = true;
                }
            }
            if (items.pollin(STDIN))
            {

                Message msg = Message.recv(key, stdinChannel);
                //dumpHeader("STDIN", msg);
                System.out.println("STDIN message recv'd");
            }
            if (items.pollin(SHELL))
            {
                Message msg = Message.recv(key, shellChannel);
                //dumpHeader("SHELL", msg);
                shellHandler(msg);
            }

        }
        LOG.info("DONE");

        heartbeatChannel.close();
        controlChannel.close();
        stdinChannel.close();
        shellChannel.close();
        iopubChannel.close();

        context.term();
    }

    public static void main(String[] args)
    {

        try
        {
            String configFile = args[0];
            String extensionsFile = args.length == 2 ? args[1]: null;
            LOG.debug("Config file: " + configFile);
            FileInputStream fis = new FileInputStream(configFile);
            ObjectMapper om = new ObjectMapper();
            Config config = om.readValue(fis, Config.class);
            SimpleKernel sk = new SimpleKernel(config);
            LOG.info("Launched kernel");
            sk.start(extensionsFile);
            LOG.info("Kernel finished");

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    int globalExecutionCount = 1;

    private void sendStatus(Message msgParent, String status) throws Exception
    {
        Message busyStatus = new Message(msgParent.header, "status");
        busyStatus.content.put("execution_state", status);
        busyStatus.send(iopubChannel, key);
    }

    class ExecutionPrintStream extends OutputStream
    {
        Message msg;
        ByteArrayOutputStream buffer;

        public ExecutionPrintStream(Message msg)
        {
            buffer = new ByteArrayOutputStream();
            this.msg = msg;
        }

        @Override
        public void write(int b) throws IOException
        {
            buffer.write(b);
        }

        public void writeMessage(String line) throws IOException
        {
            try
            {
                Message stream = new Message(msg.header, "stream");
                stream.content.put("name", "stdout");
                stream.content.put("text", line);
                stream.send(iopubChannel, key);
            }
            catch (Exception ex)
            {
                throw new IOException(ex);
            }
        }

        @Override
        public void flush() throws IOException
        {
            assert(msg != null);
            writeMessage(buffer.toString());
            buffer.reset();

        }

        @Override
        public void close() throws IOException
        {
            buffer.close();
        }
    }
    private void executeRequest(Message msg) throws Exception
    {

        LOG.debug("simple-kernel-nashorn Executing");
        sendStatus(msg, "busy");

        Message executeInput = new Message(msg.header, "execute_input");
        executeInput.content.put("execution_count", this.globalExecutionCount);
        String code = (String) msg.content.get("code");
        executeInput.content.put("code", code);
        executeInput.send(iopubChannel, key);

        Object evalOut = null;
        LOG.info("Redirecting stdout");
        ExecutionPrintStream mps = new ExecutionPrintStream(msg);
        PrintStream stdout = System.out;
        System.setOut(new PrintStream(mps, true));
        try
        {
            evalOut = scriptEngine.eval(code);
            List<String> results = serializeResults(evalOut);
            Message executeResult = new Message(msg.header, "execute_result");
            executeResult.content.put("execution_count", this.globalExecutionCount);
            Map<String, Object> data = new HashMap<>();
            data.put("text/plain", results);
            executeResult.content.put("data", data);
            executeResult.content.put("metadata", new HashMap<>());
            executeResult.send(iopubChannel, key);
            sendStatus(msg, "idle");

            Message executeReply = new Message(msg.header, "execute_reply");
            executeReply.content.put("status", "ok");
            executeReply.content.put("execution_count", globalExecutionCount);
            executeReply.content.put("user_variables", new HashMap<>());
            executeReply.content.put("payload", new ArrayList<>());
            executeReply.content.put("user_expressions", new HashMap<>());
            executeReply.identities = msg.identities;
            executeReply.send(shellChannel, key);
        }
        catch(Exception ex)
        {
            List<String> bt = Arrays.asList(ex.getStackTrace()).stream().map(f -> f.toString()).collect(Collectors.toList());
            Message errorResult = new Message(msg.header, "error");
            errorResult.content.put("ename", ex.getClass().toString());
            errorResult.content.put("evalue", ex.getMessage());
            errorResult.content.put("traceback", bt);
            errorResult.send(iopubChannel, key);
            sendStatus(msg, "idle");

            Message executeReply = new Message(msg.header, "execute_reply");
            executeReply.content.put("execution_count", this.globalExecutionCount);
            executeReply.content.put("status", "error");
            executeReply.content.put("ename", ex.getClass().toString());
            executeReply.content.put("evalue", ex.getMessage());
            executeReply.content.put("traceback", bt);
            executeReply.identities = msg.identities;
            executeReply.send(shellChannel, key);

        }
        finally
        {
            System.setOut(stdout);
            LOG.debug("Redirected stdout back");
        }

        globalExecutionCount++;
    }

    private List<String> serializeResults(Object result)
    {

        if (result == null)
        {
            return new ArrayList<>();
        }
        List<String> results = new ArrayList<>();
        final Iterator it;

        if (result instanceof Iterator)
        {
            it = (Iterator) result;
        }
        else if (result instanceof Iterable)
        {
            it = ((Iterable) result).iterator();
        }
        else if (result instanceof Object[])
        {
            it = new ArrayIterator((Object[]) result);
        }
        else if (result instanceof Map)
        {
            it = ((Map) result).entrySet().iterator();
        }
        else
        {
            it = Arrays.asList(result).iterator();
        }

        while (it.hasNext())
        {
            results.add(it.next().toString());

        }
        return results;
    }

    private void shellHandler(Message msg) throws Exception
    {

        if (msg.header.msg_type.equals("execute_request"))
        {
            executeRequest(msg);
        }
        else if (msg.header.msg_type.equals("kernel_info_request"))
        {

            Message kernelInfoReply = new Message(msg.header, "kernel_info_reply");
            kernelInfoReply.identities = msg.identities;
            kernelInfoReply.content.put("protocol_version", "5.0");
            kernelInfoReply.content.put("implementation", "simple-kernel-nashorn");
            kernelInfoReply.content.put("implementation_version", "0.0.1");

            Map<String, Object> languageInfo = new HashMap<>();
            languageInfo.put("name", "simple-kernel-nashorn");
            languageInfo.put("version", "0.0.1");
            languageInfo.put("mimetype", "");
            languageInfo.put("file_extension", ".js");
            languageInfo.put("pygments_lexer", "JavaScript");
            languageInfo.put("codemirror_code", "");
            languageInfo.put("nbconvert_exporter", "");

            kernelInfoReply.content.put("language_info", languageInfo);
            kernelInfoReply.content.put("banner", "Simple Kernel Nashorn");
            kernelInfoReply.send(shellChannel, key);
            sendStatus(msg, "idle");
        }
        else if (msg.header.msg_type.equals("history_request"))
        {
            LOG.info("unhandled history request");
        }
        else
        {
            LOG.warn("unknown msg_type " + msg.header.msg_type);
        }
    }

}
