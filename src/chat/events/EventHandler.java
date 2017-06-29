package chat.events;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import chat.bot.ChatBot;
import chat.bot.tools.MicroAsmExamples;
import chat.bot.tools.MicroAssembler;
import utils.Utils;
import static utils.Utils.parseLongs;

public abstract class EventHandler
{
	private static final boolean DEBUG = false;
	public interface Command
	{
		public abstract void run(ChatEvent event, String args);
	}
	private static final long MAX_COMMAND_TIME = 30000;// 30 seconds
	private static final long WAVE_TIMER_SLEEP = 60000*5;// 60 seconds *5
	private static final String waveRight = "o/", waveLeft = "\\o";
	private Map<String, Command> commands = new TreeMap<>();
	private Map<String, Command> builtincommands = new TreeMap<>();
	{
		Command listcommands = (ChatEvent event, String args)->{
			String message = "Available commands:\nBuiltin: ";
			String[] builtin = builtincommands.keySet().toArray(new String[0]);
			String[] cmds = commands.keySet().toArray(new String[0]);
			message+=builtin[0];
			for(int i=1;i<builtin.length;++i)
				message+=", "+builtin[i];
			message+="\nLearned: "+args;
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
			/*
			 * Examples:
			 *     Fibonacci sequence:
			 *         =a1;=b1;=c0;l1?b0;#1end;"$a, ;=cb;+ba;=ac;#7l1;end"...;
			 *     
			 */
			String message = MicroAssembler.assemble(args);
			if(message.isEmpty())
				message = "Invalid input.";
			ChatBot.replyToMessage(event, message);
		};
		Command learn = (ChatEvent event, String args)->{
			if(!args.isEmpty() && args.contains(" ")){
				String[] args2 = args.split(" ", 2);
				final String name = args2[0];
				final String text = args2[1];
				if(!commands.containsKey(name))
				{
					commands.put(name, (ChatEvent _event, String _args)->{
						ChatBot.putMessage(_event, MicroAssembler.assemble('\"'+text, _args));
					});
					ChatBot.replyToMessage(event, "Learned command: "+name);
				}
				else
					ChatBot.replyToMessage(event, "Command already exists.");
			}
			else{
			}
		};
		Command unlearn = (ChatEvent event, String args)->{
			if(commands.containsKey(args))
			{
				commands.remove(args);
				ChatBot.replyToMessage(event, "Forgot command: "+args);
			}
			else
				ChatBot.replyToMessage(event, "Command does not exists.");
		};
		Command joinroom = (ChatEvent event, String args)->{
			ChatBot.joinRoom(event.getChatSite(), parseLongs(args));
		};
		Command leaveroom = (ChatEvent event, String args)->{
			ChatBot.leaveRoom(event.getChatSite(), parseLongs(args));
		};
		Command rolldice = (ChatEvent event, String args)->{
			args=args.trim();
			String[] argarr = args.trim().split("\\s+");
			int argcount = argarr.length;
			switch(argcount){
				case 0:
					args = "1 6";
					break;
				case 1:
					args = "1 "+args;
					break;
				case 2:
					ChatBot.putMessage(event, MicroAsmExamples.rolldice(args));
					break;
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
			int argcount = argarr.length;
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
		/*Command echo = (ChatEvent event, String args)->{
			ChatBot.putMessage(event, MicroAsmExamples.echo(args));
		};*/
		Command cointoss = (ChatEvent event, String args)->{
			ChatBot.putMessage(event, MicroAsmExamples.cointoss(args));
		};
		Command eval = (ChatEvent event, String args)->{
			ChatBot.replyToMessage(event, Utils.eval(args));
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
		//builtincommands.put("echo", echo);
		builtincommands.put("cointoss", cointoss);
		builtincommands.put("coinflip", cointoss);
		builtincommands.put("eval", eval);
	}
	private String trigger;
	private volatile boolean justWaved = false;
	private Runnable waveTimer = ()->{
		try{
			Thread.sleep(WAVE_TIMER_SLEEP);
		}catch(Exception e){e.printStackTrace();}
		finally{
			justWaved=false;
		}
	};
	private boolean wave(final ChatEvent event){
		switch(event.getContent()){
			case waveRight:
				ChatBot.putMessage(event, waveLeft);
				break;
			case waveLeft:
				ChatBot.putMessage(event, waveRight);
				break;
			default:
				return false;
		}
		justWaved=true;
		new Thread(waveTimer, "WaveTimer").start();
		return true;
	}
	public abstract void handle(final ChatEvent event);
	/**
	 * Runs a command associated with the chat event, if any.
	 * @param event The chat event
	 * @return {@code true} iff the input caused a command to execute.
	 */
	protected boolean runCommand(final ChatEvent event)
	{
		System.out.println(utils.Utils.getDateTime()+" "+event.getEventType().toString()+
				" by user \""+event.getUserName()+"\" (id "+event.getUserId()+
				") in "+event.getChatSite()+
				" room \""+event.getRoomName()+"\" (id "+event.getRoomId()+
				") with content \""+event.getContent().replace("\"", "\\\"")+"\"");
		if(event.getContent()==null)
			return false;
		if(!justWaved && wave(event))
			return true;
		String content = event.getContent().trim();
		
		switch(event.getEventType()){
			case UserMentioned:
			case MessageReply:
				try{
					content = content.split(" ", 2)[1];
				}
				catch(ArrayIndexOutOfBoundsException aioobe){
					content = "";
				}
				break;
			case MessagePosted:
			case MessageEdited:
				if(!content.startsWith(trigger))
					return false;
				else break;
			default:
				throw new UnsupportedOperationException(event.getEventType().name());
		}
		
		String[] arr = content.substring(2).split(" ",2);
		
		final String command=arr[0].trim().toLowerCase();
		String extra = arr.length>1?arr[1]:"";
		final Command c;
		if(builtincommands.containsKey(command))
			c = builtincommands.get(command);
		else if(commands.containsKey(command))
			c = commands.get(command);
		else{
			System.out.println("Invalid command: "+command);
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
		}, "Command-"+command);
		
		final String cmd = command;
		countdown.schedule(new TimerTask(){
			public void run(){
				if(DEBUG)
					return;
				System.out.println("Command timed out.");
				try
				{
					Thread.State s = thread.getState();
					switch(s)
					{
						case NEW:
							throw new InternalError("Thread not started.");
						case BLOCKED:
						case RUNNABLE:
						case TIMED_WAITING:
						case WAITING:
							//Interrupt the thread
							thread.interrupt();
							Thread.sleep(1000);
							if(thread.isAlive()){
								//Kill thread
								thread.stop();
							}
							break;
						case TERMINATED:
							break;
						default:
							throw new InternalError("Unknown thread state: ");
					}
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
	public void setTrigger(String trigger)
	{
		this.trigger=trigger;
	}
	/**
	 * Adds the command to the command list.
	 * @param command The command to add
	 * @return {@code true} if the command was added, {@code false} otherwise.
	 */
	public boolean addCommand(String name, Command command)
	{
		name=name.trim().toLowerCase();
		boolean canAdd = !commands.containsKey(name);
		if(canAdd)
			commands.put(name, command);
		return canAdd;
	}
	/**
	 * Adds the command to the command list.
	 * @param command The command to add
	 * @return {@code true} if the command was added, {@code false} otherwise.
	 */
	public boolean removeCommand(String name)
	{
		name=name.trim().toLowerCase();
		boolean canAdd = commands.containsKey(name);
		if(canAdd)
			commands.remove(name);
		return canAdd;
	}
}
