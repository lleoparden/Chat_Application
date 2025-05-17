•	Introduction

This project presents a user-friendly chat application designed to facilitate chatting and communication between friends, offering all types of texting forms, sharing, posting stories and customization for a more personalized experience.


•	Main Entities

User (data class -> UserData): 

Each user has the following attributes (userID, displayName, phoneNumber, Password, userDescription, userStatus, online, lastSeen, profilePictureUrl)

Chat room (data class -> Chat):
	
Each chat room has the following attributes (ID, name, lastMessage, timestamp, participantsID, type, displayName, unreadCount)

Message (data class -> Message):
	
Each message has the following attributes (ID, chatID, senderID, content, timestamp, readStatus, messageType, voiceNoteDuration, voiceNoteLocalPath)

Story (data class -> Stories):
	
Each story entity has the following attributes (uid, displayName, profilePictureURL, stories)
List of stories uses data class -> Story:
(imageURL, storyCaption, uploadedAt)










•	Functionalities

Login / Signup: 

Greets the user when opening the app without being signed in and gives them the option whether to signup or login. In login the user enters their phone number and password (which have to match), as for the sign up the user has to enter their name, phone number and password (which has to be over 8 characters, contains a capital letter and a special character)

Local Reading / Writing: 
Handles reading and writing chat, user, and story data to local .json files for offline support. Initializes file paths for each relevant dataset (chats.json, users.json, local_user.json, local_stories.json) and creates them if they don't exist. Chats are saved locally with full metadata (IDs, names, last messages, timestamps, unread counts, participants, and chat type). During app startup or when needed, chat data is loaded from the local file and filtered to include only those where the current user is an active participant. This allows offline chat history access even without internet connection. Files are updated anytime there’s a change in the data through custom read/write utility functions.

Firebase Reading / Writing: 

The Firebase services in this project provide real-time synchronization and persistent storage for user and chat data using Firebase Realtime Database and Firestore. They handle chat updates through real-time listeners, user authentication and profile management via Firestore, and support merging remote and local data on launch with offline fallbacks. However, the project is fully functional without Firebase, as all core features operate with local storage. Firebase integration can be enabled or disabled through a boolean flag defined in the app’s resources, ensuring that remote services are optional and do not affect the app’s usability when turned off.

Uploading Images: 
The image uploading feature allows users to select and preview images using Glide, then upload them to ImgBB by converting the image to Base64 and sending it through a multipart POST request using OkHttp. Upload progress is shown via a ProgressBar and handled with a callback interface. After a successful upload, the image URL is returned, and the image can also be saved locally in the app’s internal storage under the user’s ID. Additionally, images can be downloaded from a URL and stored locally for offline access. This functionality is used in the Chat Room Page for sending images and in the Profile Page for setting or updating user and group profile pictures.


Home Page: 

Shows the list of available chats for the user, a search button that opens a search bar to look for a chat within the existing chats, a settings button to open the settings menu, a bottom navigation bar to switch between chat list and stories list pages, an “add new chat” button allowing the user to create a new chat with another user.

New Chat Page: 

Shows a search bar allowing the user to search for other users IN THEIR CONTACTS LIST using their phone numbers, which then shows a list of contacts
that allows the user to open the profile of whichever contact the user desires. Also contains a button that switches to creating a group chat instead of a private chat.

New Group Chat Page: 

Shows a text bar that allows the user to pick their desired group name, also contains a search bar allowing the user to search for other users IN THEIR CONTACTS LIST using their phone numbers, which then shows a list of contacts
that allows the user to pick and choose which contacts they want to add to the group chat.


Chat Room Page: 

Shows the name and profile picture of the other user bound to the chat, shows list of messages both sent and received, also contains a text bar allowing the user to type any message alongside an image button allowing the user to send images and a microphone icon that allows them to send recorded voice messages.

Profile Page (user & group): 

Displays profile picture of the group or user’s opened profile alongside the name, phone number, about and status (only with user) and Group description, list of group members and number of members (only with group).



Stories List Page: 

Displays list of available stories for the user to view by clicking on one 0of them with the user’s stories at the top (if available) and a button allowing the user to create their own story.

Create New Story Page: 

Displays a preview of the image to be uploaded with a text bar that allows the user to choose an image to be posted.

View Story Page: 

Shows the name and profile picture of the user who posted the story and the amount of time it was posted from, displays the story itself by displaying the image posted and the caption written over it.

Settings Page: 

Gives the user many options to choose from (Edit profile, Manage account, change Theme, Clear chat history and local data, Help, Invite friends, about, sign out)



Edit Profile Page: 

Gives the user the opportunity to customize their profile by picking their own profile picture, Name, About and Description

Account Settings Page: 

Allows the user to change more sensitive information on their account such as Phone number, Password and read receipts.

Theme Page: 

Lets the user choose their own preferred theme from light and dark mode, also gives the user the ability to choose their own chat room wallpaper or change back to the default wallpaper for a more personalized experience.



help Page: 

Displays for the user a list of frequently asked question for easier accessibility, also gives the user to ability to send customer support an email by filling in their name, email and issue with the option to add an image if they ever stumble upon a problem or bug. 

Invite Friends Page: 

Displays a search bar that allows the user to search for a contact using their name or phone number then displays a list of the valid targets and checks whether they have the app downloaded or not then gives the user the choice whether to start chatting (if the target has the app downloaded) or to invite them by sending an SMS to the target’s phone (if the target doesn’t have the app downloaded)

About Page: 

Shows the user details and information about the app such as our Email, website, legal information and app version.



