package chat.events;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import chat.ChatSite;
import chat.bot.ChatBot;
import chat.bot.tools.MicroAsmExamples;
import chat.bot.tools.MicroAssembler;
import chat.io.ErrorMessages;
import chat.io.ErrorMessages.ErrorType;
import utils.Utils;
import static utils.Utils.parseLongs;
import static utils.Utils.urlencode;
import static utils.Utils.urldecode;

public abstract class EventHandler
{
	private static final boolean DEBUG = Utils.isdebuggerrunning();
	private interface Command
	{
		public abstract void run(ChatEvent event, String args);
	}
	private static final int MIN_MENTION_LENGTH = 3;
	private static final long MAX_COMMAND_TIME = 30000;// 30 seconds
	private static final long WAVE_TIMER_SLEEP = 60000*5;// 60 seconds *5
	private static final String waveRight = "o/", waveLeft = "\\o";
	private static final String cmdfileext = ".txt";
	private static volatile int instanceNumber = 1;
	private final String savedir = System.getProperty("user.dir")+"/SEChatBot/"
			+(instanceNumber++);
	private final String cmdSaveDirectory = savedir+"/commands/";
	private final String roomSaveDirectory = savedir+"/rooms/";
	private Map<String, Command> commands = new TreeMap<>();
	private Map<String, Command> builtincommands = new TreeMap<>();
	private String trigger;
	private volatile boolean justWaved = false;
	private static final String regex_emoji_fitz = "(?:\uD83C[\uDFFB-\uDFFF])?";
	private static final String wave_emoji_plain = "\uD83D\uDC4B";
	private static final String regex_wave_emoji = wave_emoji_plain + regex_emoji_fitz;
	
	private Runnable waveTimer = ()->{
		try{
			Thread.sleep(WAVE_TIMER_SLEEP);
		}catch(Exception e){e.printStackTrace();}
		finally{
			justWaved=false;
		}
	};
	private boolean wave(final ChatEvent event){
		String content = event.getContent().trim();
		switch(content){
			case waveRight:
				ChatBot.putMessage(event, waveLeft);
				break;
			case waveLeft:
				ChatBot.putMessage(event, waveRight);
				break;
			default:
				if(content.matches(regex_wave_emoji)){
					ChatBot.putMessage(event, wave_emoji_plain);
					break;
				}
				return false;
		}
		justWaved=true;
		new Thread(waveTimer, "WaveTimer").start();
		return true;
	}
	public EventHandler(){
		
	}
	public EventHandler(String trigger){
		this.trigger=trigger;
	}
	public abstract void handle(final ChatEvent event);
	private static volatile int threadNumber = 1;
	private static final java.util.function.Supplier<String> myPingable=()->{ 
		return "@"+ChatBot.getMyUserName().replaceAll("\\s","");
	};
	/**
	 * Runs a command associated with the chat event, if any.
	 * @param event The chat event
	 * @return {@code true} iff the input caused a command to execute.
	 */
	protected boolean runCommand(final ChatEvent event)
	{
		if(DEBUG)
			System.out.println(event.getEventType().toString()+
					"(msg id "+event.getMessageId()+") in "+
					event.getChatSite().getAbbreviation()+" room "+event.getRoomName()+
					"(room id "+event.getRoomId()+")"+
					" by user \""+event.getUserName()+"\" (id "+event.getUserId()+
					") \""+event.getEscapedContent()+'\"');
		if(event.getContent()==null)
			return false;
		if(!justWaved && wave(event))
			return true;
		String content = getSterilizedContent(event);
		switch(event.getEventType()){
			case MessageReply:
				break;
			case UserMentioned:
				break;
			case MessagePosted:
			case MessageEdited:
				if(!content.startsWith(trigger))
					return false;
				else
					break;
			default:
				throw new UnsupportedOperationException(event.getEventType().name());
		}
		
		String[] arr = (content.startsWith(trigger) ? content.substring(trigger.length()) : content).split(" ",2);
		
		final String command=arr[0].trim().toLowerCase();
		String extra = arr.length>1?arr[1]:"";
		final Command c;
		if(builtincommands.containsKey(command))
			c = builtincommands.get(command);
		else if(commands.containsKey(command))
			c = commands.get(command);
		else{
			switch(event.getEventType())
			{
				case UserMentioned:
				case MessageReply:
					break;
				case MessagePosted:
				case MessageEdited:
					System.out.println("Invalid command: "+command);
					break;
				default:
					System.out.println("Invalid command: "+command);
					break;
			}
			return false;
		}
		
		final String args = extra;
		Timer countdown = new Timer();
		//Start a new Thread to run the command
		Thread thread = new Thread(new Runnable()
		{
			public void run()
			{
				c.run(event, args);
				countdown.cancel();
			}
		}, "Command-"+command+"-"+(threadNumber++));
		final String cmd = command;
		countdown.schedule(new TimerTask(){
			public void run(){
				if(DEBUG)
					return;
				System.out.println("Command timed out.");
				try
				{
					Utils.forceKillThread(thread);
				}
				catch(IllegalArgumentException | InterruptedException e)
				{
					System.err.println("Failed to stop thread for command \""+cmd+
							"\" with arguments \""+args+"\"");
					e.printStackTrace();
				}
			}
		}, MAX_COMMAND_TIME);
		thread.start();
		return true;
	}
	private static Pattern regex_message_reply = Pattern.compile("^:\\d+ "),
			regex_mention = Pattern.compile("@((?:[^\\s!?();:,\\/+&<]){"
					+ MIN_MENTION_LENGTH + "3,})");
	
	private String getSterilizedContent(ChatEvent event)
	{
		String content = event.getContent().trim();
		String myName = myPingable.get();
		//TODO Remove instances of (text that could ping myName) from (content)
		Matcher m = regex_message_reply.matcher(content);
		switch(event.getEventType()){
			case MessageEdited:
				break;
			case MessageMovedIn:
				break;
			case MessageMovedOut:
				break;
			case MessagePosted:
				break;
			case MessageReply:
				content = m.replaceFirst("");
				break;
			case UserMentioned:
				break;
			default:
				break;
		}
		// TODO Auto-generated method stub
		return content;
	}
	public final void setTrigger(String trigger)
	{
		this.trigger=trigger;
	}
	private Command putCommand(String name, String text){
		name=name.trim().toLowerCase();
		return commands.put(name, (ChatEvent _event, String _args)->{
			ChatBot.putMessage(_event, MicroAssembler.assemble('\"'+text, _args));
		});
	}
	/**
	 * Adds the command to the command list.
	 * @param text The assembly command to add
	 * @return {@code true} if the command was added, {@code false} otherwise.
	 */
	public final boolean addCommand(String name, String text)
	{
		name=name.trim().toLowerCase();
		synchronized(commands){
			boolean canAdd = !(commands.containsKey(name) || builtincommands.containsKey(name));
			if(canAdd){
				putCommand(name, text);
				writeCommandFile(name, text);
			}
			return canAdd;
		}
	}
	/**
	 * Removes the command from the command list.
	 * @param command The command to remove
	 * @return {@code true} if the command was removed, {@code false} otherwise.
	 */
	public final boolean removeCommand(String name)
	{
		name=name.trim().toLowerCase();
		synchronized(commands){
			boolean canRemove = commands.containsKey(name);
			if(canRemove){
				commands.remove(name);
				removeCommandFile(name);
			}
			return canRemove;
		}
	}
	private boolean writeCommandFile(String name, String text)
	{
		File f = new File(cmdSaveDirectory+urlencode_cmd(name.toLowerCase())+cmdfileext);
		
		f.getParentFile().mkdirs();
		if(f.exists() && f.isFile())
			f.delete();
		try
		{
			if(f.createNewFile())
			{
				FileOutputStream fos = new FileOutputStream(f);
				fos.write(text.getBytes());
				fos.close();
				return true;
			}
		}
		catch(IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	private boolean removeCommandFile(String name)
	{
		File f = new File(cmdSaveDirectory+urlencode_cmd(name)+cmdfileext);
		f.mkdirs();
		return f.exists() && f.isFile() && f.delete();
	}
	private boolean writeRoomFile(ChatSite site, Long[] rooms){
		boolean success = true;
		for(long r : rooms)
		{
			File f = new File(roomSaveDirectory+site.name()+"/"+r);
			if(!f.exists())
				success &= f.mkdirs();
		}
		return success;
	}
	private boolean deleteRoomFile(ChatSite site, Long[] rooms){
		boolean deletedall = true;
		for(long r : rooms)
		{
			File f = new File(roomSaveDirectory+site.name()+"/"+r);
			if(f.exists())
				deletedall &= f.delete();
		}
		return deletedall;
	}
	/*Built in commands*/
	{
		Command listcommands = (ChatEvent event, String args)->{
			String message = "Available commands:\nBuiltin: ";
			String[] builtin = builtincommands.keySet().toArray(new String[0]);
			String[] cmds = commands.keySet().toArray(new String[0]);
			message+=builtin[0];
			for(int i=1;i<builtin.length;++i)
				message+=", "+builtin[i];
			message+="\nLearned: ";
			if(cmds.length>0)
			{
				message+=cmds[0];
				for(int i=1;i<cmds.length;++i)
					message+=", "+cmds[i];
			}
			else
				message+="none";
			ChatBot.putMessage(event, message);
		};
		Command assembly = (ChatEvent event, String args)->{
			String message = MicroAssembler.assemble(args);//TODO
			if(message.isEmpty())
				message = ErrorMessages.getErrorText(event, ErrorType.BADINPUT);
			ChatBot.replyToMessage(event, message);
		};
		Command learn = (ChatEvent event, String args)->{
			ChatBot.replyToMessage(event, "This command is disabled until further notice.");
			/*
			if(!args.isEmpty() && args.contains(" ")){
				String[] args2 = args.split(" ", 2);
				final String name = args2[0];
				final String text = args2[1];
				if(addCommand(name, text))
					ChatBot.replyToMessage(event, "Learned command: "+name);
				else
					ChatBot.replyToMessage(event, ErrorMessages.getErrorText(event, ErrorType.CMD_ALREADYEXISTS));
			}
			else{
			}
			 */
		};
		Command unlearn = (ChatEvent event, String args)->{
			ChatBot.replyToMessage(event, "This command is disabled until further notice.");
			/*
			if(removeCommand(args))
				ChatBot.replyToMessage(event, "Forgot command: "+args);
			else
			{
				if(builtincommands.containsKey(args))
					ChatBot.replyToMessage(event, ErrorMessages.getErrorText(event, ErrorType.CMD_UNFORGETABLE));
				else
					ChatBot.replyToMessage(event, ErrorMessages.getErrorText(event, ErrorType.CMD_NOTFOUND));
			}
			 */
		};
		Command joinroom = (ChatEvent event, String args)->{
			ChatSite site = event.getChatSite();
			Long[] rooms = parseLongs(args);
			ChatBot.joinRoom(site, rooms);
			writeRoomFile(site, rooms);			
		};
		Command leaveroom = (ChatEvent event, String args)->{
			ChatSite site = event.getChatSite();
			Long[] rooms = parseLongs(args);
			ChatBot.leaveRoom(site, rooms);
			deleteRoomFile(site, rooms);
		};
		Command rolldice = (ChatEvent event, String args)->{
			args=args.trim();
			String[] argarr = args.split("\\s+|(?=<\\d)d(?=\\d)|(?=<\\\\d)d|d(?=\\\\d)");
			int argcount = args.contains(" ")?argarr.length:0;
			switch(argcount){
				case 0:
					args = "1 6";
					break;
				case 1:
					args = "1 "+args;
					break;
				case 2:
					ChatBot.putMessage(event, MicroAsmExamples.rolldice(args));
					return;
				default:
					args = argarr[0]+" "+argarr[1];
					break;
			}
			ChatBot.putMessage(event, MicroAsmExamples.rolldice(args));
		};
		Command fibonacci = (ChatEvent event, String args)->{
			if(args.trim().isEmpty())
				args = "0";
			ChatBot.putMessage(event, MicroAsmExamples.fibonacci(args));
		};
		Command rand = (ChatEvent event, String args)->{
			args=args.trim();
			String[] argarr = args.trim().split("\\s+");
			int argcount = args.contains(" ")?argarr.length:0;
			switch(argcount){
				case 0:
					ChatBot.putMessage(event, MicroAsmExamples.rand0(args));
					break;
				case 1:
					ChatBot.putMessage(event, MicroAsmExamples.rand1(args));
					break;
				case 2:
					ChatBot.putMessage(event, MicroAsmExamples.rand2(args));
					break;
				default:
					args = argarr[0]+" "+argarr[1];
					ChatBot.putMessage(event, MicroAsmExamples.rand0(args));
					break;
			}
		};
		Command eval = ChatBot::replyToMessageByEval;
		Command room = (ChatEvent event, String args)->{
			ChatBot.putMessage(event, MicroAssembler.assemble("\"https://"+event.getChatSite().getUrl()+"/rooms/$0", args));
		};
		Command wotd = (ChatEvent event, String args)->{
			ChatBot.replyToMessage(event, Utils.getWotd());
		};
		builtincommands.put("help", listcommands);
		builtincommands.put("list", listcommands);
		builtincommands.put("listcommands", listcommands);
		builtincommands.put("asm", assembly);
		builtincommands.put("learn", learn);
		builtincommands.put("unlearn", unlearn);
		builtincommands.put("joinroom", joinroom);
		builtincommands.put("leaveroom", leaveroom);
		builtincommands.put("rolldice", rolldice);
		builtincommands.put("fibonacci", fibonacci);
		builtincommands.put("rand", rand);
		builtincommands.put("eval", eval);
		builtincommands.put("room", room);
		builtincommands.put("wotd", wotd);
		
		final String basiccmdsfname = "basic_commands.properties";
		try{
			Utils.loadProperties(basiccmdsfname).entrySet().stream().forEach((Map.Entry<Object, Object> entry)->{
				Command cmd = (event, args)->{
					ChatBot.putMessage(event, MicroAssembler.assemble(entry.getValue().toString(), MicroAssembler.escapeArgs(args)));
				};
				Arrays.stream(entry.getKey().toString().split(",| ")).forEach(entrycmdname->{
					builtincommands.put(entrycmdname.toLowerCase(), cmd);
				});
			});
		}
		catch(IOException e){
			System.err.println("Warning: Did not find \""+basiccmdsfname+"\", continuing anyways...");
		}
	}
	/*Re-learns previously learned commands*/
	{
		File cmddir = new File(cmdSaveDirectory);
		cmddir.mkdirs();
		File[] cmdfiles = cmddir.listFiles();
		System.out.println("Loading external commands...");
		for(File f : cmdfiles)
		{
			String cmdname = urldecode_cmd(f.getName().endsWith(cmdfileext) ? 
					f.getName().substring(0, f.getName().length() - cmdfileext.length())
					: f.getName());
			System.out.println("Loading command: "+cmdname);
			try
			{
				String text = "";
				FileReader reader = new FileReader(f);
				int ch;
				while((ch=reader.read())!=-1)
					text+=(char)ch;
				reader.close();
				putCommand(cmdname, text);
			}
			catch(IOException e){
				new InternalError("Failed to load command: "+cmdname, e).printStackTrace();
			}
		}
		System.out.println("Done loading commands...");
	}
	public static String urlencode_cmd(String name){
		return urlencode(name.toLowerCase()).replace('%', 'P');
	}
	public static String urldecode_cmd(String name)
	{
		return urldecode(name.replace('P', '%').toLowerCase());
	}
	public final String getCmdSaveDirectory(){
		return cmdSaveDirectory;
	}
	public final String getRoomSaveDirectory()
	{
		return roomSaveDirectory;
	}
}
