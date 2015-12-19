package chatserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * administers all Users
 */
public class UserMap extends ConcurrentHashMap<String, User> {
	
	/**
	 * verify username and password and if correct sets user
	 * status to online = true;
	 * username != null; password != null
	 * 
	 * @param username	name of the user who wants to login
	 * @param password	users password
	 * @return response if user could be successfully logged in or not
	 */
	public synchronized String login(String username, String password) {
		User user = get(username);
		
		if(user == null || !password.equals(user.getPassword()))
			return "Wrong username or password.";
		else if(user.isOnline())
			return "User already logged in.";
		else {
			user.setOnline(true);
			put(username, user);
			return "Successfully logged in.";
		}
	}
	
	/**
	 * logout user, set user status online = false;
	 * username != null; user must be logged in
	 * 
	 * @param username	user, who wants to logout
	 * @return response if user could be successfully logged out or not
	 */
	public synchronized String logout(String username) {
		User user = get(username);
		
		if(user == null) {
			return "Wrong username.";
		} else if(!user.isOnline()) {
			return "User must be logged in.";
		} else {
			user.setOnline(false);
			put(username, user);
			return "Successfully logged out.";
		}
	}
	
	/**
	 * register Port of user
	 * username != null, ipPort != null
	 * 
	 * @param username user, who wants to register port
	 * @param ipPort port of user
	 */
	public synchronized String registerPort(String username, String ipPort) {
		User user = get(username);
		
		if(user == null) {
			return "Wrong username.";
		} else if(ipPort.isEmpty() || ipPort.length() == 0) {
			return "No valid ipPort.";
		} else {
			user.setIpPort(ipPort);
			put(username, user);
			return "Successfully registerd address for " + username;
		}
	}
	
	/**
	 * lookup user port
	 * username != null
	 * 
	 * @param username
	 */
	public synchronized String lookUpPort(String username) {
		User user = get(username);
		
		if(user == null) {
			return "Wrong username.";
		} else {
			if(user.getIpPort() == null || user.getIpPort().length() == 0)
				return username + " has no register address";
			else
				return user.getIpPort();
		}
	}
	
	public synchronized String listUsers() {
		String output = "";
		List<String> userNames = getSortedUserNames();		
		
		for(int i = 0; i < userNames.size(); i++) {
			User user = get(userNames.get(i));
			output += (i+1) +". " + user.getName() + " ";
			if(user.isOnline())
				output += "online \n";
			else
				output += "offline \n";	
		}
		return output;		
	}
	
	public synchronized String listOnlineUsers() {
		String output = "Online users: \n";
		List<String> userNames = getSortedUserNames();		
		
		for(int i = 0; i < userNames.size(); i++) {
			User user = get(userNames.get(i));
			if(user.isOnline())
				output += "* " + user.getName() + "\n";
		}
		return output;	
	}
	
	// sort usernames in alphabetical order and retruns them
	private List<String> getSortedUserNames() {
		List<String> userNames = new ArrayList<String>();
		userNames.addAll(keySet());
		Collections.sort(userNames);
		
		return userNames;
	}

}
