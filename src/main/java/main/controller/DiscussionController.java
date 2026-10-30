package main.controller;

import jakarta.servlet.http.HttpSession;
import main.entity.User;
import main.service.AccessPolicy;
import main.service.DiscussionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

@Controller
@RequestMapping("/thao-luan")
public class DiscussionController {
    private final DiscussionService discussions;
    private final AccessPolicy access;
    public DiscussionController(DiscussionService discussions,AccessPolicy access) { this.discussions=discussions;this.access=access; }

    private User viewer(HttpSession session) { return (User)session.getAttribute("loggedInUser"); }
    private boolean common(Model model,HttpSession session) {
        User user=viewer(session);boolean staff=access.canModerate(user);
        model.addAttribute("user",user);model.addAttribute("isStaff",staff);
        if(user!=null) {
            if(session.getAttribute("discussionToken")==null) session.setAttribute("discussionToken",UUID.randomUUID().toString());
            model.addAttribute("discussionToken",session.getAttribute("discussionToken"));
        }
        return staff;
    }
    private User requireUser(HttpSession session,String token) {
        User user=viewer(session);
        if(user==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Hãy đăng nhập.");
        Object expected=session.getAttribute("discussionToken");
        if(!(expected instanceof String value)||token==null||!MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Yêu cầu không hợp lệ. Hãy tải lại trang.");
        }
        return user;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String type,
                       @RequestParam(defaultValue="active") String state,@RequestParam(defaultValue="0") int page,Model model,HttpSession session) {
        boolean staff=common(model,session);
        q=DiscussionService.keyword(q);type=DiscussionService.category(type);state=DiscussionService.visibility(state,staff);
        model.addAttribute("q",q);model.addAttribute("type",type);model.addAttribute("state",state);
        model.addAttribute("topics",discussions.topics(q,type,state,staff,page));
        return "discussions";
    }
    @GetMapping("/tao")
    public String createForm(Model model,HttpSession session) {
        if(viewer(session)==null) return "redirect:/login";
        common(model,session);form(model,null,"Tạo chủ đề","/thao-luan/tao","", "",false,false);
        return "discussion-form";
    }
    private void form(Model model,Long id,String heading,String action,String title,String content,boolean notification,boolean reply) {
        model.addAttribute("topicId",id);model.addAttribute("heading",heading);model.addAttribute("formAction",action);
        model.addAttribute("formTitle",title);model.addAttribute("formContent",content);model.addAttribute("formNotification",notification);model.addAttribute("replyEdit",reply);
    }
    @PostMapping("/tao")
    public String create(@RequestParam String title,@RequestParam String content,@RequestParam(defaultValue="false") boolean notification,
                         @RequestParam String discussionToken,Model model,HttpSession session) {
        if(viewer(session)==null) return "redirect:/login";
        User user=requireUser(session,discussionToken);common(model,session);
        try { return "redirect:/thao-luan/"+discussions.create(user.getUserId(),title,content,notification); }
        catch(ResponseStatusException e) {
            model.addAttribute("formError",e.getReason());form(model,null,"Tạo chủ đề","/thao-luan/tao",title,content,notification,false);
            return "discussion-form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id,@RequestParam(defaultValue="0") int page,Model model,HttpSession session) {
        boolean staff=common(model,session);var topic=discussions.topic(id,staff);
        if(!topic.deleted()&&shouldCountView(session,id)) { discussions.view(id);topic=discussions.topic(id,staff); }
        model.addAttribute("topic",topic);model.addAttribute("replies",discussions.replies(id,staff,page));
        return "discussion-detail";
    }
    @SuppressWarnings("unchecked")
    private boolean shouldCountView(HttpSession session,long id) {
        synchronized(session) {
            Object saved=session.getAttribute("discussionViews");
            LinkedHashMap<Long,Long> seen=saved instanceof LinkedHashMap ? (LinkedHashMap<Long,Long>)saved : new LinkedHashMap<>();
            long now=Instant.now().getEpochSecond();Long last=seen.get(id);
            if(last!=null&&now-last<1800) return false;
            seen.remove(id);seen.put(id,now);
            while(seen.size()>200) seen.remove(seen.keySet().iterator().next());
            session.setAttribute("discussionViews",seen);return true;
        }
    }
    @GetMapping("/{id}/sua")
    public String editForm(@PathVariable long id,Model model,HttpSession session) {
        if(viewer(session)==null) return "redirect:/login";
        boolean staff=common(model,session);var topic=discussions.topic(id,staff);
        if(topic.userId()!=viewer(session).getUserId()||topic.deleted()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        form(model,id,"Sửa chủ đề","/thao-luan/"+id+"/sua",topic.title(),topic.content(),topic.notification(),false);
        return "discussion-form";
    }
    @PostMapping("/{id}/sua")
    public String edit(@PathVariable long id,@RequestParam String title,@RequestParam String content,@RequestParam(defaultValue="false") boolean notification,
                       @RequestParam String discussionToken,Model model,HttpSession session) {
        if(viewer(session)==null) return "redirect:/login";
        User user=requireUser(session,discussionToken);common(model,session);
        try {discussions.editTopic(id,user.getUserId(),title,content,notification);return "redirect:/thao-luan/"+id;}
        catch(ResponseStatusException e) {
            if(e.getStatusCode().value()!=400) throw e;
            model.addAttribute("formError",e.getReason());form(model,id,"Sửa chủ đề","/thao-luan/"+id+"/sua",title,content,notification,false);
            return "discussion-form";
        }
    }
    @PostMapping("/{id}/tra-loi")
    public String reply(@PathVariable long id,@RequestParam String content,@RequestParam String discussionToken,HttpSession session,RedirectAttributes redirects) {
        if(viewer(session)==null) return "redirect:/login";
        User user=requireUser(session,discussionToken);
        try {
            long replyId=discussions.postReply(id,user.getUserId(),content);
            int lastPage=Math.max(0,discussions.replies(id,access.canModerate(user),0).getTotalPages()-1);
            return "redirect:/thao-luan/"+id+"?page="+lastPage+"#comment-"+replyId;
        }
        catch(ResponseStatusException e) {redirects.addFlashAttribute("discussionError",e.getReason());redirects.addFlashAttribute("replyContent",content);return "redirect:/thao-luan/"+id+"#reply-form";}
    }
    @GetMapping("/{id}/binh-luan/{commentId}/sua")
    public String replyForm(@PathVariable long id,@PathVariable long commentId,Model model,HttpSession session) {
        if(viewer(session)==null) return "redirect:/login";
        boolean staff=common(model,session);var topic=discussions.topic(id,staff);var reply=discussions.reply(id,commentId,staff);
        if(topic.deleted()||reply.deleted()||reply.userId()!=viewer(session).getUserId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        form(model,id,"Sửa bình luận","/thao-luan/"+id+"/binh-luan/"+commentId+"/sua","",reply.content(),false,true);
        return "discussion-form";
    }
    @PostMapping("/{id}/binh-luan/{commentId}/sua")
    public String editReply(@PathVariable long id,@PathVariable long commentId,@RequestParam String content,@RequestParam String discussionToken,Model model,HttpSession session) {
        if(viewer(session)==null) return "redirect:/login";
        User user=requireUser(session,discussionToken);common(model,session);
        try {discussions.editReply(id,commentId,user.getUserId(),content);return "redirect:/thao-luan/"+id+"#replies";}
        catch(ResponseStatusException e) {
            if(e.getStatusCode().value()!=400) throw e;
            model.addAttribute("formError",e.getReason());form(model,id,"Sửa bình luận","/thao-luan/"+id+"/binh-luan/"+commentId+"/sua","",content,false,true);
            return "discussion-form";
        }
    }
    @PostMapping("/{id}/quan-ly")
    public String moderate(@PathVariable long id,@RequestParam String action,@RequestParam(required=false) Long commentId,
                          @RequestParam String discussionToken,HttpSession session) {
        if(viewer(session)==null) return "redirect:/login";
        User user=requireUser(session,discussionToken);discussions.moderate(id,user.getUserId(),action,commentId);
        return "redirect:/thao-luan/"+id;
    }
}
