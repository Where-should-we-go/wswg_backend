package com.ssafy.wswg.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.model.service.TripService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/trip")
@RequiredArgsConstructor
public class TripController {
	private final TripService tripService;
	
	@GetMapping("/list")
	public String getAllAttractions(Model model) {
		model.addAttribute("attractions", tripService.readAttractions());
		
		return "list";
	}
	
	@GetMapping("/detail")
	public String getAttraction(@RequestParam int no, Model model) {
		model.addAttribute("attraction", tripService.readAttraction(no));
		return "detail";
	}
	
	@GetMapping("/mvRegister")
	public String showRegister() {
		return "register";
	}
	
	@GetMapping("/mvUpdate")
	public String showUpdate(@RequestParam int no, Model model) {
		model.addAttribute("attraction", tripService.readAttraction(no));
		return "update";
	}
	
	@PostMapping("/register")
	public String register(@ModelAttribute AttractionDto attractionDto) {
		tripService.createAttraction(attractionDto);
		
		return "redirect:/trip/list";	
	}
	
	@PostMapping("/modify")
	public String update(@RequestParam int no, @ModelAttribute AttractionDto attractionDto) {
		attractionDto.setNo(no);
		tripService.updateAttraction(attractionDto);
		
		return "redirect:/trip/list";
	}
	
	@GetMapping("/delete")
	public String delete(@RequestParam int no) {
		tripService.deleteAttraction(no);
		return "list";
	}
}
