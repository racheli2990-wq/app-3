package com.racheli.offlinenotes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LocalAssistant {
    private LocalAssistant() {}
    static String answer(String question,NotesDb db) {
        String q=question==null?"":question.trim().toLowerCase(Locale.ROOT);
        if(q.isEmpty())return "כתבי שאלה, ואחפש במידע ששמור במכשיר.";
        List<Note> notes=db.all();List<TaskItem> tasks=db.allTasks();
        if(any(q,"כמה פתקים","מספר פתקים","כמה הערות"))return "יש בכספת שלך "+notes.size()+" פתקים.";
        if(any(q,"משימות פתוחות","מה נשאר","מה לעשות","המשימות שלי","משימות")) {
            List<String> open=new ArrayList<>();
            for(TaskItem t:tasks)if(!t.done)open.add("• "+t.title+(t.dueDate.isEmpty()?"":" — "+t.dueDate));
            return open.isEmpty()?"אין כרגע משימות פתוחות. כל הכבוד!":"המשימות הפתוחות שלך:\n"+join(open,"\n");
        }
        if(any(q,"סכם","סיכום","תמצת")) {
            List<String> snippets=new ArrayList<>();
            for(Note n:notes){String s=(n.title+" — "+n.body).trim();if(!s.equals("—")&&!s.isEmpty())snippets.add("• "+clip(s,180));if(snippets.size()==6)break;}
            return snippets.isEmpty()?"עדיין אין פתקים שאפשר לסכם.":"סיכום מהיר של הפתקים האחרונים שלך:\n"+join(snippets,"\n");
        }
        List<String> matches=new ArrayList<>();
        for(Note n:notes)if(match(q,(n.title+" "+n.body).toLowerCase(Locale.ROOT))) {
            matches.add("פתק: "+n.title+"\n"+clip(n.body,220));if(matches.size()==5)break;
        }
        for(TaskItem t:tasks)if(match(q,(t.title+" "+t.detail).toLowerCase(Locale.ROOT))) {
            matches.add("משימה: "+t.title+(t.done?" (הושלמה)":" (פתוחה)"));if(matches.size()==7)break;
        }
        return matches.isEmpty()?"לא מצאתי התאמה ברורה במידע המקומי. נסי מילות חיפוש אחרות, או טעני מודל AI מקומי לשיחה מתקדמת."
            :"מצאתי מידע רלוונטי בכספת המקומית:\n\n"+join(matches,"\n\n");
    }
    static String buildContext(String question,NotesDb db) {
        StringBuilder out=new StringBuilder("מידע מקומי מתוך LifeVault. השתמש רק בו כשעונים על שאלות אישיות, וציין כשאין מידע.\n");
        String q=question==null?"":question.toLowerCase(Locale.ROOT);int count=0;
        for(Note n:db.all())if(q.isEmpty()||match(q,(n.title+" "+n.body).toLowerCase(Locale.ROOT))||count<3) {
            out.append("\nפתק: ").append(n.title).append("\n").append(clip(n.body,900)).append("\n");if(++count>=8)break;
        }
        int tasks=0;
        for(TaskItem t:db.allTasks()) {
            out.append("\nמשימה: ").append(t.title).append(" | ").append(t.done?"הושלמה":"פתוחה");
            if(!t.dueDate.isEmpty())out.append(" | מועד: ").append(t.dueDate);
            if(!t.detail.isEmpty())out.append(" | ").append(clip(t.detail,220));
            out.append("\n");if(++tasks>=12)break;
        }
        return out.toString();
    }
    private static boolean any(String q,String... terms){for(String t:terms)if(q.contains(t))return true;return false;}
    private static boolean match(String q,String text) {
        for(String word:q.split("[\\s,.;:!?]+"))if(word.length()>1&&text.contains(word))return true;return false;
    }
    private static String clip(String s,int max){if(s==null)return "";s=s.trim();return s.length()<=max?s:s.substring(0,max)+"…";}
    private static String join(List<String> a,String sep){StringBuilder b=new StringBuilder();for(String s:a){if(b.length()>0)b.append(sep);b.append(s);}return b.toString();}
}
